package com.recipick.domain.payment.event;

import com.recipick.domain.payment.service.PaymentDelayQueue;
import com.recipick.domain.payment.service.PaymentDelayTask;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 결제를 만든 주문 생성 트랜잭션이 커밋된 뒤에만 만료 작업을 예약한다. 트랜잭션 안에서 Redis에 넣으면 롤백된 결제의 작업이
// 남는다. 롤백이면 이 리스너가 실행되지 않는다. 결제 생성은 바깥 트랜잭션을 강제하므로(MANDATORY) 트랜잭션 없이 발행된
// 이벤트는 호출 구조의 버그라 fallbackExecution을 켜지 않는다. Redis 한 번이라 비동기로 돌리지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpirationScheduleListener {

    private final PaymentDelayQueue paymentDelayQueue;
    private final Clock clock;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void schedule(PaymentCreatedEvent event) {
        // expiresAt은 Clock 시간대(Asia/Seoul)의 LocalDateTime으로 저장되므로 같은 시간대로 바꾼다
        Instant executeAt = event.expiresAt().atZone(clock.getZone()).toInstant();
        // 신규 등록이다. Fallback이 이미 지금 시각으로 넣었다면 그보다 늦은 만료 시각으로 되돌리지 않는다
        try {
            paymentDelayQueue.enqueueIfEarlier(PaymentDelayTask.paymentExpire(event.paymentId()), executeAt);
            log.info("결제 만료 작업 예약: paymentId={}, executeAt={}", event.paymentId(), executeAt);
        } catch (DataAccessException e) {
            // 주문·결제는 이미 커밋됐다. 결제가 IN_PROGRESS로 남아 Fallback이 다시 등록한다.
            // Redis 장애만 잡는다 — 코드 버그까지 장애처럼 감추지 않는다
            log.error("결제 만료 작업 예약 실패 — Fallback 복구 대상: paymentId={}, expiresAt={}",
                event.paymentId(), event.expiresAt(), e);
        }
    }
}
