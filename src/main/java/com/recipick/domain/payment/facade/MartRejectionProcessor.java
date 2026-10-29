package com.recipick.domain.payment.facade;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.facade.OrderFacade;
import com.recipick.domain.order.service.MartRejectRequestResult;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.MartRejectionPaymentResult;
import com.recipick.domain.payment.dto.MartRejectionPreparation;
import com.recipick.domain.payment.dto.response.MartRejectionResponse;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundFailureType;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.MartRejectionOutcome;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.payment.service.PointCommandService;
import com.recipick.domain.product.dto.StockQuantity;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// 마트 거절 환불의 상태 변경 트랜잭션. 회원 취소의 PaymentCancelProcessor와 같은 TX1(접수)·TX2(반영) 구조지만
// PG 취소가 명확히 실패해도 주문을 정상 접수로 되돌리지 않는다 — 결제만 PAID로 돌아가고 주문·마트는 거절 상태로 남는다.
// 두 실패 정책이 한 Processor에 섞이지 않도록 따로 둔다. 업무 결과는 예외가 아니라 반환값으로 돌려준다 — 안에서 던지면 롤백된다.
// 잠금 순서 PAYMENT → ORDERS → MEMBER_POINT → POINT_EARNING → 재고
@Slf4j
@Component
@RequiredArgsConstructor
public class MartRejectionProcessor {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final OrderFacade orderFacade;
    private final PointCommandService pointCommandService;
    private final AdminAuditRecorder adminAuditRecorder;

    // TX1. 거절을 접수한다. 주문 거절·결제 CANCEL_REQUESTED·ADMIN 환불·감사 로그를 한 트랜잭션으로 커밋하고
    // 실행이 필요한 PENDING 결과를 돌려준다. 실행 중 서버가 죽어도 환불 행이 남아 Worker·Fallback이 이어받는다.
    // 포인트·재고는 여기서 건드리지 않는다 — PG 취소가 성공한 TX2에서만 한 번 되돌린다
    @Transactional
    public MartRejectionPreparation prepare(AdminRole role, Long adminMartId, Long adminId, Long orderId,
                                            String idempotencyKey, String reason) {
        // 마트 범위를 결제 잠금·멱등 판정보다 먼저 본다. 순서가 바뀌면 다른 마트 관리자가 남의 주문의 결제 상태나
        // 같은 키의 결과를 응답 차이로 알 수 있다. 마트 ID 값만 읽고 잠그지 않으므로 잠금 순서 PAYMENT → ORDERS는 그대로다
        Long martId = orderService.getMartIdOf(orderId);
        if (role == AdminRole.MART_ADMIN && !martId.equals(adminMartId)) {
            throw new BusinessException(AuthErrorCode.MART_ACCESS_DENIED);
        }

        Payment payment = paymentService.getByOrderIdForUpdate(orderId);
        // 결제 전 주문은 주문을 잠그거나 거절 전이를 부르지 않고 끝낸다
        if (payment.getStatus() == PaymentStatus.IN_PROGRESS) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_PAID);
        }

        // 같은 키 = 같은 거절 작업. 주문 거절을 다시 부르지 않고 그 환불의 상태로 답한다
        Optional<Refund> existing = paymentService.findRefundByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), payment, reason);
        }

        // 다른 키로 취소가 진행 중이다. 그 결과를 기다려야 하므로 새 환불을 만들지 않는다
        if (payment.getStatus() == PaymentStatus.CANCEL_REQUESTED) {
            throw new BusinessException(CommonErrorCode.DUPLICATE_REQUEST_IN_PROGRESS);
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }

        // 결제가 PAID여도 ADMIN 환불이 실패한 거절 주문일 수 있다. 주문이 ALREADY_REJECTED로 판정하므로
        // 새 환불을 만들지 않는다 — 실패한 거절의 재처리는 2차 관리자 재처리 API의 몫이다
        MartRejectRequestResult orderResult =
            orderService.requestMartRejection(role, adminMartId, adminId, orderId, reason);
        if (orderResult != MartRejectRequestResult.REJECTED) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_REJECTABLE);
        }
        payment.requestCancel();
        Refund refund = paymentService.requestAdminRefund(payment, reason, idempotencyKey);

        // 새 거절이 실제로 접수됐을 때만 남긴다. SUPER_ADMIN은 토큰에 마트가 없으므로 주문의 마트를 쓴다.
        // 상태 전후 값은 주문 상태 이력 두 건에 이미 남고, 거절 사유는 자유 입력 원문이라 넣지 않는다
        adminAuditRecorder.record(new AdminAuditCommand(AdminAuditAction.ORDER_REJECT, orderId, martId, Map.of()));
        return MartRejectionPreparation.execute(result(MartRejectionOutcome.PENDING, payment, refund));
    }

    // TX2. PortOne 결과를 반영한다. 실행기·웹훅이 같은 환불을 여러 번 넘겨도 끝난 환불은 다시 바꾸지 않고 그 결과를 돌려준다.
    // 끝난 환불이라도 결제 상태와 맞지 않는 조합이면 "이미 반영됨"으로 덮지 않고 예외로 멈춘다 — 불일치를 숨기지 않기 위해
    @Transactional
    public MartRejectionPaymentResult apply(Long paymentId, Long refundId, PortOneCancelResult result) {
        Payment payment = paymentService.getByIdForUpdate(paymentId);
        Refund refund = paymentService.getRefundById(refundId);
        // 두 ID는 실행기·웹훅이 스냅샷에서 그대로 넘긴다. 다른 결제의 환불이거나 마트 거절 환불이 아니면 호출자 버그다
        if (refund.getRequestedBy() != RefundRequester.ADMIN
            || !refund.getPayment().getId().equals(payment.getId())) {
            throw new IllegalStateException(
                "마트 거절 환불이 아닙니다. paymentId=" + paymentId + ", refundId=" + refundId);
        }

        switch (refund.getStatus()) {
            case COMPLETED -> {
                requirePaymentStatus(payment, refund, PaymentStatus.CANCELED);
                return result(MartRejectionOutcome.COMPLETED, payment, refund);
            }
            case FAILED -> {
                // CANCELED 허용은 FAILED ADMIN 환불이 후속 외부 취소(PortOne 콘솔 전액 취소)로 복구된 경우를 위한 것이다.
                // 그때 기존 ADMIN 환불은 FAILED 이력으로 남고 결제는 새 EXTERNAL 환불과 함께 CANCELED가 된다
                if (payment.getStatus() != PaymentStatus.PAID && payment.getStatus() != PaymentStatus.CANCELED) {
                    throw mismatch(payment, refund);
                }
                return result(failureOutcome(refund), payment, refund);
            }
            case REQUESTED -> requirePaymentStatus(payment, refund, PaymentStatus.CANCEL_REQUESTED);
        }

        requireSameCancellationId(refund, result.cancellationId());
        requireNotBoundToOtherRefund(refund, result.cancellationId());

        return switch (result.outcome()) {
            case SUCCEEDED -> result.canceledAt() == null
                ? confirmLater(payment, refund, result, "SUCCEEDED인데 취소 시각이 없음")
                : complete(payment, refund, result);
            case REQUESTED -> confirmLater(payment, refund, result, "PG 비동기 처리 중");
            case FAILED -> fail(payment, refund, result.cancellationId(), RefundFailureType.PG_FAILED, result.detail());
            case REJECTED -> fail(payment, refund, null, RefundFailureType.REQUEST_REJECTED, result.detail());
            // 404·상태 불일치 409·timeout·5xx·해석 불가. 성공도 실패도 단정할 수 없어 접수 상태를 그대로 둔다
            case MISMATCH, UNCONFIRMED -> result(MartRejectionOutcome.PENDING, payment, refund);
        };
    }

    // 실행기가 TX2까지 가지 않았을 때 거절 API가 응답할 현재 상태를 읽는다. 잠그지 않는다 — 응답용 스냅샷이다
    @Transactional(readOnly = true)
    public MartRejectionPaymentResult getCurrentResult(Long paymentId, Long refundId) {
        Payment payment = paymentService.getById(paymentId);
        Refund refund = paymentService.getRefundById(refundId);
        return switch (refund.getStatus()) {
            case COMPLETED -> result(MartRejectionOutcome.COMPLETED, payment, refund);
            case FAILED -> result(failureOutcome(refund), payment, refund);
            case REQUESTED -> result(MartRejectionOutcome.PENDING, payment, refund);
        };
    }

    // 같은 키의 재요청. 다른 결제·다른 사유·마트 거절 환불이 아닌 행이면 키를 잘못 재사용한 것이다.
    // 진행 중인 환불은 새 환불·새 키 없이 같은 실행기로 다시 보낸다 — PortOne 재요청 여부는 실행기의 GET이 정한다
    private MartRejectionPreparation replay(Refund refund, Payment payment, String reason) {
        if (refund.getRequestedBy() != RefundRequester.ADMIN
            || !refund.getPayment().getId().equals(payment.getId())
            || !refund.getReason().equals(reason)) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH);
        }
        return switch (refund.getStatus()) {
            case COMPLETED ->
                MartRejectionPreparation.finished(result(MartRejectionOutcome.COMPLETED, payment, refund));
            case FAILED ->
                MartRejectionPreparation.finished(result(failureOutcome(refund), payment, refund));
            case REQUESTED -> MartRejectionPreparation.execute(result(MartRejectionOutcome.PENDING, payment, refund));
        };
    }

    private MartRejectionPaymentResult complete(Payment payment, Refund refund, PortOneCancelResult result) {
        payment.markCanceled(result.canceledAt());
        refund.complete(result.cancellationId(), result.canceledAt());
        // 주문은 CANCEL_REQUESTED·MART_REJECTED로 접수돼 있어 기존 환불 완료 경로로 닫힌다. 마트 상태는 REJECTED 그대로다
        List<StockQuantity> items = orderFacade.markRefunded(payment.getOrderId());
        pointCommandService.restoreUsageAndCancelEarning(payment.getOrderId());
        orderFacade.restoreStock(items);
        log.info("마트 거절 환불 완료: paymentId={}, refundId={}, orderId={}, cancellationId={}",
            payment.getId(), refund.getId(), payment.getOrderId(), result.cancellationId());
        return result(MartRejectionOutcome.COMPLETED, payment, refund);
    }

    // 결과를 확정하지 못했지만 PortOne이 취소 작업을 만들었다. 웹훅·재실행이 이 ID로 환불을 찾는다
    private MartRejectionPaymentResult confirmLater(Payment payment, Refund refund, PortOneCancelResult result,
                                                    String why) {
        refund.recordRequested(result.cancellationId());
        log.warn("마트 거절 환불 결과 확인 필요({}): refundId={}, cancellationId={}",
            why, refund.getId(), result.cancellationId());
        return result(MartRejectionOutcome.PENDING, payment, refund);
    }

    // 결제만 PAID로 되돌린다. 주문은 마트가 이미 처리할 수 없다고 판정했으므로 CANCEL_REQUESTED·REJECTED로 남긴다 —
    // 회원 취소와 달리 restorePaidAfterMemberCancelFailure를 부르지 않는다. 돈이 PG에 남아 운영이 봐야 하므로 ERROR로 남긴다.
    // 거절(400·401·403)의 분류 로그는 PortOnePaymentClient가 이미 남겼다
    private MartRejectionPaymentResult fail(Payment payment, Refund refund, String cancellationId,
                                            RefundFailureType failureType, String detail) {
        refund.fail(cancellationId, failureType, detail);
        payment.restorePaid();
        log.error("마트 거절 환불 실패 — 결제만 PAID 복귀, 주문은 거절 상태 유지: paymentId={}, refundId={}, "
                + "orderId={}, cancellationId={}, failureType={}, detail={}",
            payment.getId(), refund.getId(), payment.getOrderId(), cancellationId, failureType, detail);
        return result(failureOutcome(refund), payment, refund);
    }

    // 환불 행 하나가 PortOne 취소 작업 하나라 한 번 정해진 ID는 바뀌지 않는다. Refund도 같은 불변식을 막지만
    // 결제 상태를 먼저 바꾼 뒤에야 걸리므로, 상태를 바꾸기 전에 원인이 드러나는 메시지로 멈춘다
    private static void requireSameCancellationId(Refund refund, String cancellationId) {
        String existing = refund.getPortoneCancellationId();
        if (StringUtils.hasText(cancellationId) && existing != null && !existing.equals(cancellationId)) {
            throw new IllegalStateException("마트 거절 환불에 이미 다른 PortOne 취소 ID가 연결돼 있습니다. refundId="
                + refund.getId() + ", existing=" + existing + ", incoming=" + cancellationId);
        }
    }

    // 후보를 고른 뒤 PAYMENT 잠금을 기다리는 사이 다른 경로가 같은 취소 ID를 다른 환불에 먼저 묶었을 수 있다.
    // UNIQUE도 커밋을 막지만 flush 예외로 끝나 원인을 읽기 어렵다. 잠금 아래에서 명시적으로 막는다
    private void requireNotBoundToOtherRefund(Refund refund, String cancellationId) {
        if (!StringUtils.hasText(cancellationId)) {
            return;
        }
        Optional<Refund> bound = paymentService.findRefundByPortoneCancellationId(cancellationId);
        if (bound.isPresent() && !bound.get().getId().equals(refund.getId())) {
            throw new IllegalStateException("PortOne 취소 ID가 다른 환불에 이미 연결돼 있습니다. refundId=" + refund.getId()
                + ", cancellationId=" + cancellationId + ", boundRefundId=" + bound.get().getId());
        }
    }

    private static void requirePaymentStatus(Payment payment, Refund refund, PaymentStatus expected) {
        if (payment.getStatus() != expected) {
            throw mismatch(payment, refund);
        }
    }

    private static IllegalStateException mismatch(Payment payment, Refund refund) {
        return new IllegalStateException("마트 거절 환불과 결제 상태가 일치하지 않습니다. paymentId=" + payment.getId()
            + ", paymentStatus=" + payment.getStatus() + ", refundId=" + refund.getId()
            + ", refundStatus=" + refund.getStatus());
    }

    // 같은 트랜잭션·같은 시점의 결제·환불로 결과를 만든다. 커밋 뒤에는 다른 요청이 둘 중 하나를 바꿨을 수 있다
    private static MartRejectionPaymentResult result(MartRejectionOutcome outcome, Payment payment, Refund refund) {
        return new MartRejectionPaymentResult(outcome, MartRejectionResponse.of(payment, refund));
    }

    // 실패 종류를 DB에 남겨 둔 이유. 같은 키 재요청이 원래 응답(409 / 500)을 문자열 해석 없이 재현한다
    private static MartRejectionOutcome failureOutcome(Refund refund) {
        if (refund.getFailureType() == null) {
            throw new IllegalStateException("실패한 마트 거절 환불에 실패 종류가 없습니다. refundId=" + refund.getId());
        }
        return switch (refund.getFailureType()) {
            case PG_FAILED -> MartRejectionOutcome.PG_FAILED;
            case REQUEST_REJECTED -> MartRejectionOutcome.REQUEST_REJECTED;
        };
    }
}
