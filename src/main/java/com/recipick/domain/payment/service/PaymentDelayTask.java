package com.recipick.domain.payment.service;

import java.util.Objects;
import java.util.Optional;

// 지연 큐의 작업 하나. 큐에는 종류와 ID만 넣고 상태·금액은 항상 DB에서 다시 읽는다
public record PaymentDelayTask(
    PaymentDelayTaskType type,
    Long id
) {
    private static final String DELIMITER = ":";

    public PaymentDelayTask {
        Objects.requireNonNull(type, "type은 필수입니다.");
        Objects.requireNonNull(id, "id는 필수입니다.");

        // DB PK(WEBHOOK_EVENT.id·REFUND.id·PAYMENT.id)라 1 이상이다. 깨진 member가 0·음수로 들어오면 parse가 빈 값으로 거른다
        if (id <= 0) {
            throw new IllegalArgumentException("id는 1 이상이어야 합니다: " + id);
        }
    }

    public static PaymentDelayTask webhookProcess(Long webhookEventId) {
        return new PaymentDelayTask(PaymentDelayTaskType.WEBHOOK_PROCESS, webhookEventId);
    }

    public static PaymentDelayTask refundReconcile(Long refundId) {
        return new PaymentDelayTask(PaymentDelayTaskType.REFUND_RECONCILE, refundId);
    }

    // 회원 환불 재확인(REFUND_RECONCILE)과 같은 REFUND.id를 쓰지만 접두사가 달라 큐에서 서로 다른 member다
    public static PaymentDelayTask systemRefundExecute(Long refundId) {
        return new PaymentDelayTask(PaymentDelayTaskType.SYSTEM_REFUND_EXECUTE, refundId);
    }

    // SYSTEM 환불 실행과 같은 REFUND.id를 쓰지만 접두사가 달라 큐에서 서로 다른 member다
    public static PaymentDelayTask adminRefundExecute(Long refundId) {
        return new PaymentDelayTask(PaymentDelayTaskType.ADMIN_REFUND_EXECUTE, refundId);
    }

    // 환불 작업과 같은 숫자 ID가 와도 접두사가 달라 큐에서 서로 다른 member다
    public static PaymentDelayTask paymentExpire(Long paymentId) {
        return new PaymentDelayTask(PaymentDelayTaskType.PAYMENT_EXPIRE, paymentId);
    }

    // Redis ZSET member. 예: WEBHOOK_PROCRESS:100
    public String member() {
        return type.name() + DELIMITER + id;
    }

    // 큐에서 꺼낸 문자열을 되돌린다. 형식이 깨졌으면 빈 값 ㅡ 호출자가 WARN으로 남기고 버린다
    public static Optional<PaymentDelayTask> parse(String member) {
        if (member ==null) {
            return Optional.empty();
        }
        String[] parts = member.split(DELIMITER, 2);
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            return Optional.of(new PaymentDelayTask(PaymentDelayTaskType.valueOf(parts[0]), Long.parseLong(parts[1])));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
