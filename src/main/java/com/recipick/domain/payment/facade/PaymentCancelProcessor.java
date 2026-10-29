package com.recipick.domain.payment.facade;

import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.facade.OrderFacade;
import com.recipick.domain.order.service.MemberCancelRequestResult;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.PaymentCancelPreparation;
import com.recipick.domain.payment.dto.PaymentCancelResult;
import com.recipick.domain.payment.dto.response.PaymentCancelResponse;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundFailureType;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.PaymentCancelOutcome;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.payment.service.PointCommandService;
import com.recipick.domain.product.dto.StockQuantity;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 결제 취소의 상태 변경 트랜잭션. 결제 후 취소는 PortOne 호출을 사이에 두고 TX1(prepare)·TX2(apply)로 나뉜다.
// 두 메서드 모두 PAYMENT 잠금으로 시작하고, 업무 결과는 예외가 아니라 반환값으로 돌려준다 — 안에서 던지면 롤백돼 버린다.
// 잠금 순서 PAYMENT → ORDERS → MEMBER_POINT → POINT_EARNING → 재고. 결제 후 취소의 포인트는 TX1(접수)에서 건드리지 않고
// PG 환불이 성공한 TX2에서만 되돌린다 — 먼저 돌려주면 환불이 실패해 결제가 PAID로 돌아와도 포인트만 돌아간 채 남는다
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCancelProcessor {

    // PortOne이 같은 Idempotency-Key에 같은 응답을 보장하는 시간(현재 문서 기준 3시간). 이 안이면 같은 서버 키로 다시 불러도
    // 이중 취소가 아니다. 외부 정책값이라 PortOne이 바꾸면 함께 갱신한다
    private static final Duration PORTONE_IDEMPOTENCY_WINDOW = Duration.ofHours(3);

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final OrderFacade orderFacade;
    private final PointCommandService pointCommandService;
    private final Clock clock;

    // TX1. 취소를 접수한다. 결제 전 취소는 여기서 끝나고, 결제 후 취소는 CANCEL_REQUESTED·REFUND REQUESTED를 커밋한 뒤
    // PortOne 호출 인자를 돌려준다 — 호출 중 서버가 죽어도 환불 행이 남아 재조회·재시도의 근거가 된다.
    // 끝나는 경우의 응답은 트랜잭션 안에서 같은 시점의 결제·환불로 만든다. 커밋 뒤 따로 읽으면 다른 요청이 끼어든 값이 섞일 수 있다
    @Transactional
    public PaymentCancelPreparation prepare(Long memberId, Long paymentId, String idempotencyKey, String reason) {
        Payment payment = paymentService.getByIdForUpdate(paymentId);
        Long orderId = payment.getOrderId();

        // 멱등 판정보다 먼저 본다. 순서가 바뀌면 남의 결제가 이미 취소됐는지를 200/404 차이로 알 수 있게 된다
        if (!orderService.getMemberIdOf(orderId).equals(memberId)) {
            log.warn("다른 회원의 결제에 취소 시도: memberId={}, paymentId={}", memberId, paymentId);
            throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }

        // 같은 키 = 같은 환불 작업. 새 환불을 만들지 않고 그 행의 상태로 답한다
        Optional<Refund> existing = paymentService.findRefundByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), payment, reason);
        }

        if (payment.getStatus() == PaymentStatus.IN_PROGRESS) {
            return cancelBeforePay(payment, reason);
        }
        // 결제 전 취소는 키를 저장하지 않는다. 이미 그렇게 종료된 상태면 같은 취소 로직을 다시 돌리지 않는다
        if (isCanceledBeforePay(payment)) {
            return finishedBeforePay(payment);
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            return requestCancelAfterPay(payment, idempotencyKey, reason);
        }
        // 다른 키로 취소가 진행 중이다. 그 요청의 결과를 기다려야 하므로 새 환불을 만들지 않는다
        if (payment.getStatus() == PaymentStatus.CANCEL_REQUESTED) {
            throw new BusinessException(CommonErrorCode.DUPLICATE_REQUEST_IN_PROGRESS);
        }
        // CANCELED, 다른 사유의 FAILED
        throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
    }

    // TX2. PortOne 응답을 반영한다. PAID로 되돌리는 건 PortOne이 FAILED를 명시했거나 처리 전에 거절한 경우뿐이고,
    // 결과를 모르는 응답은 상태를 두고 PENDING으로 돌려준다 — 돈은 나갔는데 주문이 살아 있는 사고를 막기 위해.
    // 응답 본문도 여기서 만든다. 실패 outcome이어도 예외는 던지지 않는다 — 커밋이 먼저고 예외는 Facade 몫이다
    @Transactional
    public PaymentCancelResult apply(Long paymentId, Long refundId, PortOneCancelResult result) {
        Payment payment = paymentService.getByIdForUpdate(paymentId);
        Refund refund = paymentService.getRefundById(refundId);
        // 두 ID는 Facade가 TX1 결과에서 그대로 넘긴다. 다른 결제의 환불이거나 회원 환불이 아니면(같은 결제의 SYSTEM 환불 등)
        // 업무 오류가 아니라 호출자 버그다. 1-5에서 호출 경로가 늘어나기 전에 여기서 막는다
        if (refund.getRequestedBy() != RefundRequester.MEMBER
            || !refund.getPayment().getId().equals(payment.getId())) {
            throw new IllegalStateException(
                "회원 취소의 환불이 아닙니다. paymentId=" + paymentId + ", refundId=" + refundId);
        }

        // 같은 키의 동시 재요청이나 웹훅이 먼저 반영했을 수 있다. 끝난 환불은 다시 바꾸지 않고 그 결과를 돌려준다
        if (refund.getStatus() == RefundStatus.COMPLETED) {
            return afterPay(PaymentCancelOutcome.CANCELED, payment, refund);
        }
        if (refund.getStatus() == RefundStatus.FAILED) {
            return afterPay(failureOutcome(refund), payment, refund);
        }

        return switch (result.outcome()) {
            case SUCCEEDED -> result.canceledAt() == null
                ? confirmLater(payment, refund, result, "SUCCEEDED인데 취소 시각이 없음")
                : complete(payment, refund, result);
            case REQUESTED -> confirmLater(payment, refund, result, "PG 비동기 처리 중");
            case FAILED -> restorePaid(payment, refund, result.cancellationId(), RefundFailureType.PG_FAILED,
                result.detail());
            case REJECTED -> restorePaid(payment, refund, null, RefundFailureType.REQUEST_REJECTED, result.detail());
            // 404·상태 불일치 409·timeout·5xx·해석 불가. 성공도 실패도 단정할 수 없어 CANCEL_REQUESTED/REQUESTED를 그대로 둔다
            case MISMATCH, UNCONFIRMED -> afterPay(PaymentCancelOutcome.PENDING, payment, refund);
        };
    }

    private PaymentCancelPreparation cancelBeforePay(Payment payment, String reason) {
        payment.fail(PaymentFailureReason.CANCELED_BEFORE_PAY, null);
        List<StockQuantity> items =
            orderFacade.closeByPaymentFailure(payment.getOrderId(), CancelReasonCode.MEMBER_CANCELED, reason);
        pointCommandService.restoreUsageAndCancelEarning(payment.getOrderId());
        orderFacade.restoreStock(items);
        return finishedBeforePay(payment);
    }

    private PaymentCancelPreparation finishedBeforePay(Payment payment) {
        return PaymentCancelPreparation.finished(new PaymentCancelResult(
            PaymentCancelOutcome.CANCELED_BEFORE_PAY, PaymentCancelResponse.beforePay(payment)));
    }

    private PaymentCancelPreparation requestCancelAfterPay(Payment payment, String idempotencyKey, String reason) {
        // 주문이 먼저 판정한다 — 마트가 준비를 시작했으면 결제 쪽은 아무것도 바꾸지 않는다
        MemberCancelRequestResult orderResult = orderService.markMemberCancelRequested(payment.getOrderId(), reason);
        if (orderResult == MemberCancelRequestResult.MART_ALREADY_PREPARING) {
            throw new BusinessException(PaymentErrorCode.ORDER_ALREADY_PREPARING);
        }
        if (orderResult == MemberCancelRequestResult.INVALID_ORDER_STATUS) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
        payment.requestCancel();
        Refund refund = paymentService.requestMemberRefund(payment, reason, idempotencyKey);
        return PaymentCancelPreparation.callPortOne(refund.getId(), payment.getPortonePaymentId(), payment.getAmount());
    }

    // 같은 키의 재요청. 다른 결제·다른 사유·회원 환불이 아닌 행이면 키를 잘못 재사용한 것이다
    private PaymentCancelPreparation replay(Refund refund, Payment payment, String reason) {
        if (refund.getRequestedBy() != RefundRequester.MEMBER
            || !refund.getPayment().getId().equals(payment.getId())
            || !refund.getReason().equals(reason)) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH);
        }
        return switch (refund.getStatus()) {
            case COMPLETED ->
                PaymentCancelPreparation.finished(afterPay(PaymentCancelOutcome.CANCELED, payment, refund));
            case FAILED -> PaymentCancelPreparation.finished(afterPay(failureOutcome(refund), payment, refund));
            // 취소 ID가 없으면 PortOne에 닿았는지 모른다. 멱등 보장 시간 안이면 같은 서버 키로 다시 물어도 이중 취소가 아니다.
            // ID가 있거나 시간이 지났으면 재호출 대신 웹훅·재조회에 맡긴다
            case REQUESTED -> canRetryPortOne(refund)
                ? PaymentCancelPreparation.callPortOne(
                refund.getId(), payment.getPortonePaymentId(), payment.getAmount())
                : PaymentCancelPreparation.finished(afterPay(PaymentCancelOutcome.PENDING, payment, refund));
        };
    }

    private boolean canRetryPortOne(Refund refund) {
        return refund.getPortoneCancellationId() == null
            && refund.getRequestedAt().plus(PORTONE_IDEMPOTENCY_WINDOW).isAfter(LocalDateTime.now(clock));
    }

    private PaymentCancelResult complete(Payment payment, Refund refund, PortOneCancelResult result) {
        payment.markCanceled(result.canceledAt());
        refund.complete(result.cancellationId(), result.canceledAt());
        List<StockQuantity> items = orderFacade.markRefunded(payment.getOrderId());
        pointCommandService.restoreUsageAndCancelEarning(payment.getOrderId());
        orderFacade.restoreStock(items);
        return afterPay(PaymentCancelOutcome.CANCELED, payment, refund);
    }

    // 결과를 확정하지 못했지만 PortOne이 취소 작업을 만들었다. 웹훅·재조회가 이 ID로 환불을 찾는다
    private PaymentCancelResult confirmLater(Payment payment, Refund refund, PortOneCancelResult result, String why) {
        refund.recordRequested(result.cancellationId());
        log.warn("PortOne 취소 결과 확인 필요({}): refundId={}, cancellationId={}",
            why, refund.getId(), result.cancellationId());
        return afterPay(PaymentCancelOutcome.PENDING, payment, refund);
    }

    // 거절(400·401·403)의 ERROR 로그는 응답을 분류한 PortOnePaymentClient가 이미 남겼다. 여기서는 무엇을 되돌렸는지 남긴다
    private PaymentCancelResult restorePaid(Payment payment, Refund refund, String cancellationId,
                                            RefundFailureType failureType, String detail) {
        refund.fail(cancellationId, failureType, detail);
        payment.restorePaid();
        orderService.restorePaidAfterMemberCancelFailure(payment.getOrderId());
        log.warn("PortOne 취소 실패로 PAID 복귀: paymentId={}, refundId={}, failureType={}, detail={}",
            payment.getId(), refund.getId(), failureType, detail);
        return afterPay(failureOutcome(refund), payment, refund);
    }

    // 같은 트랜잭션·같은 시점의 결제·환불로 응답을 만든다. 커밋 뒤에는 다른 요청이 둘 중 하나를 바꿨을 수 있다
    private static PaymentCancelResult afterPay(PaymentCancelOutcome outcome, Payment payment, Refund refund) {
        return new PaymentCancelResult(outcome, PaymentCancelResponse.afterPay(payment, refund));
    }

    // 실패 종류를 DB에 남겨 둔 이유. 같은 키 재요청이 원래 응답(409 / 500)을 문자열 해석 없이 재현한다
    private PaymentCancelOutcome failureOutcome(Refund refund) {
        return switch (refund.getFailureType()) {
            case PG_FAILED -> PaymentCancelOutcome.PG_FAILED;
            case REQUEST_REJECTED -> PaymentCancelOutcome.REQUEST_REJECTED;
        };
    }

    private boolean isCanceledBeforePay(Payment payment) {
        return payment.getStatus() == PaymentStatus.FAILED
            && payment.getFailureReason() == PaymentFailureReason.CANCELED_BEFORE_PAY;
    }
}
