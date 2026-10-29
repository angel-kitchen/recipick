package com.recipick.domain.payment.dto.response;

import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import java.time.LocalDateTime;

// 마트 거절 API의 응답. Processor가 트랜잭션 안에서 같은 시점의 결제·환불로 만든다 — 커밋 뒤 따로 읽으면 다른 경로가 바꾼 값이 섞인다.
// 카드 거절은 주문이 CANCEL_REQUESTED·REJECTED로 PG 결과를 기다리고, 0원(POINTS_ONLY) 거절은 같은 트랜잭션에서 REFUNDED까지 끝난다.
// 주문 상태는 결제 수단으로 정해지므로 주문·마트 상태는 넣지 않는다
public record MartRejectionResponse(
    Long orderId,
    Long paymentId,
    PaymentStatus paymentStatus,
    LocalDateTime canceledAt,
    Long refundId,
    RefundStatus refundStatus,
    Long refundAmount
) {

    // 0원 결제는 PG를 거치지 않아 환불 작업이 없다. 취소 시각은 서버가 내부 취소를 끝낸 시각이다
    public static MartRejectionResponse pointsOnly(Payment payment) {
        return new MartRejectionResponse(
            payment.getOrderId(),
            payment.getId(),
            payment.getStatus(),
            payment.getCanceledAt(),
            null,
            null,
            0L
        );
    }

    public static MartRejectionResponse of(Payment payment, Refund refund) {
        return new MartRejectionResponse(
            payment.getOrderId(),
            payment.getId(),
            payment.getStatus(),
            payment.getCanceledAt(),
            refund.getId(),
            refund.getStatus(),
            refund.getAmount()
        );
    }
}
