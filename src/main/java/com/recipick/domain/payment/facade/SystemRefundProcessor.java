package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundFailureType;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// SYSTEM 환불의 PortOne 결과를 환불 행에 반영하는 트랜잭션. 회원 취소의 PaymentCancelProcessor.apply와 같은 자리지만
// 결제·주문·재고는 건드리지 않는다 — SYSTEM 환불은 결제가 이미 FAILED, 주문이 CANCELED로 닫힌 뒤 PG에 남은 돈을 돌려주는 것이라
// 완료돼도 결제를 CANCELED로 바꾸지 않는다. 결제가 FAILED가 아니면 이 전제가 깨진 것이라 반영하지 않는다.
// 환불 변경은 결제 도메인 규칙대로 PAYMENT 잠금 아래에서 직렬화하고, 업무 결과는 예외가 아니라 환불 상태로 돌려준다
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemRefundProcessor {

    private final PaymentService paymentService;

    // 두 ID는 실행 Facade·웹훅이 스냅샷에서 그대로 넘긴다. 다른 결제의 환불이거나 SYSTEM 환불이 아니면 호출자 버그라 여기서 막는다.
    // 이미 끝난 환불은 다시 바꾸지 않고 그 상태를 돌려준다 — 웹훅·재실행·Fallback이 같은 환불을 여러 번 넘겨도 반영은 한 번이다
    @Transactional
    public RefundStatus apply(Long paymentId, Long refundId, PortOneCancelResult result) {
        Payment payment = paymentService.getByIdForUpdate(paymentId);
        Refund refund = paymentService.getRefundById(refundId);
        if (refund.getRequestedBy() != RefundRequester.SYSTEM
            || !refund.getPayment().getId().equals(payment.getId())) {
            throw new IllegalStateException(
                "SYSTEM 환불이 아닙니다. paymentId=" + paymentId + ", refundId=" + refundId);
        }

        // 멱등 검사보다 먼저 본다. 끝난 환불이라도 결제가 FAILED가 아니면 데이터 이상이라 조용히 넘기지 않는다
        if (payment.getStatus() != PaymentStatus.FAILED) {
            throw new IllegalStateException(
                "SYSTEM 환불 대상 결제가 FAILED 상태가 아닙니다. paymentId=" + paymentId
                    + ", status=" + payment.getStatus());
        }

        if (refund.getStatus() != RefundStatus.REQUESTED) {
            log.info("이미 끝난 SYSTEM 환불이라 다시 반영하지 않음: refundId={}, status={}", refundId, refund.getStatus());
            return refund.getStatus();
        }

        switch (result.outcome()) {
            // 완료 시각은 PG가 확인한 값이어야 한다. 없으면 완료로 바꾸지 않고 ID만 남겨 재확인에 맡긴다
            case SUCCEEDED -> {
                if (result.canceledAt() == null) {
                    confirmLater(refund, result, "SUCCEEDED인데 취소 시각이 없음");
                } else {
                    refund.complete(result.cancellationId(), result.canceledAt());
                    log.info("SYSTEM 환불 완료: refundId={}, paymentId={}, cancellationId={}",
                        refundId, paymentId, result.cancellationId());
                }
            }
            case REQUESTED -> confirmLater(refund, result, "PG 비동기 처리 중");
            case FAILED -> fail(refund, result.cancellationId(), RefundFailureType.PG_FAILED, result.detail());
            case REJECTED -> fail(refund, null, RefundFailureType.REQUEST_REJECTED, result.detail());
            // 404·상태 불일치 409·timeout·5xx·해석 불가. 성공도 실패도 단정할 수 없어 REQUESTED를 그대로 둔다
            case MISMATCH, UNCONFIRMED -> { }
        }
        return refund.getStatus();
    }

    // 결과를 확정하지 못했지만 PortOne이 취소 작업을 만들었다. 웹훅·재실행이 이 ID로 환불을 찾는다
    private void confirmLater(Refund refund, PortOneCancelResult result, String why) {
        refund.recordRequested(result.cancellationId());
        log.warn("SYSTEM 환불 결과 확인 필요({}): refundId={}, cancellationId={}",
            why, refund.getId(), result.cancellationId());
    }

    // 돈이 PG에 남은 채 자동 반환이 끝났다. 관리자 재처리는 2차라 지금은 운영이 보도록 ERROR로 남긴다.
    // 거절(400·401·403)의 분류 로그는 PortOnePaymentClient가 이미 남겼다
    private void fail(Refund refund, String cancellationId, RefundFailureType failureType, String detail) {
        refund.fail(cancellationId, failureType, detail);
        log.error("SYSTEM 환불 실패 — 운영 확인 필요: refundId={}, paymentId={}, failureType={}, detail={}",
            refund.getId(), refund.getPayment().getId(), failureType, detail);
    }
}
