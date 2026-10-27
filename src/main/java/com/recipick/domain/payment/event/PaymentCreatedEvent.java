package com.recipick.domain.payment.event;

import java.time.LocalDateTime;
import java.util.Objects;

// 결제가 만들어진 주문 생성 트랜잭션 안에서 발행. 커밋 뒤 만료 작업을 예약하는 데 필요한 값만 담는다 ㅡ
// 엔티티를 넘기지 않는 것은 커밋 뒤에는 영속성 컨텍스트와 분리될 수 있고, 만료 작업이 실행될 때 DB에서 다시 읽기 때문
public record PaymentCreatedEvent(
    Long paymentId,
    LocalDateTime expiresAt
) {
    public PaymentCreatedEvent {
        Objects.requireNonNull(paymentId, "paymentId는 필수입니다.");
        Objects.requireNonNull(expiresAt, "expiresAt은 필수입니다.");
        // 큐 member가 DB PK로 만들어지므로 저장 전 값(null·0)으로 발행하면 호출 쪽 버그다
        if (paymentId <= 0) {
            throw new IllegalArgumentException("paymentId는 1 이상이어야 합니다: " + paymentId);
        }
    }
}
