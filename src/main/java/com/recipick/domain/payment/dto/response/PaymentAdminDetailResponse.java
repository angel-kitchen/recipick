package com.recipick.domain.payment.dto.response;

import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import java.time.LocalDateTime;
import java.util.List;

// 관리자 결제 상세. 운영 확인용이라 PortOne 결제·거래 ID와 환불 시도 전체 이력을 함께 준다.
// 환불 이력은 실패 후 재시도한 시도까지 모두 담고, 순서는 호출자가 넘긴 대로(최신 시도부터) 유지한다
public record PaymentAdminDetailResponse(
    Long paymentId,
    Long orderId,
    Long memberId,
    PaymentMethod paymentMethod,
    Long amount,
    PaymentStatus paymentStatus,
    PaymentFailureReason failureReason,
    String portonePaymentId,
    String portoneTransactionId,
    LocalDateTime expiresAt,
    LocalDateTime paidAt,
    LocalDateTime canceledAt,
    List<RefundHistory> refunds
) {
    public static PaymentAdminDetailResponse of(Payment payment, Long memberId, List<Refund> refunds) {
        return new PaymentAdminDetailResponse(
            payment.getId(),
            payment.getOrderId(),
            memberId,
            payment.getPaymentMethod(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getFailureReason(),
            payment.getPortonePaymentId(),
            payment.getPortoneTransactionId(),
            payment.getExpiresAt(),
            payment.getPaidAt(),
            payment.getCanceledAt(),
            refunds.stream().map(RefundHistory::from).toList()
        );
    }

    // 환불 시도 한 건. 멱등 키는 클라이언트 요청 식별용이라 운영 응답에 넣지 않는다
    public record RefundHistory(
        Long refundId,
        Long amount,
        RefundStatus status,
        RefundRequester requestedBy,
        String reason,
        String portoneCancellationId,
        String failureReason,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
    ) {
        public static RefundHistory from(Refund refund) {
            return new RefundHistory(
                refund.getId(),
                refund.getAmount(),
                refund.getStatus(),
                refund.getRequestedBy(),
                refund.getReason(),
                refund.getPortoneCancellationId(),
                refund.getFailureReason(),
                refund.getRequestedAt(),
                refund.getCompletedAt()
            );
        }
    }
}
