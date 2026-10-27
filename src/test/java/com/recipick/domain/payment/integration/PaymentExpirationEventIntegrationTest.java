package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.payment.event.PaymentCreatedEvent;
import com.recipick.domain.payment.service.PaymentDelayTask;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

// 결제 생성 이벤트가 실제 Spring 트랜잭션의 커밋에만 반응해 실제 Redis에 만료 작업을 남기는지 본다.
// 리스너는 DB를 읽지 않으므로 결제 행 없이 이벤트만 발행한다. create()가 이벤트를 발행하는 것은 PaymentCommandServiceTest가 맡는다
class PaymentExpirationEventIntegrationTest extends PaymentFlowIntegrationSupport {

    // 실제 결제 행이 없는 가짜 ID다. 다른 테스트의 결제 ID와 겹치지 않게 크게 잡는다
    private static final Long COMMIT_PAYMENT_ID = 9_900_001L;
    private static final Long ROLLBACK_PAYMENT_ID = 9_900_002L;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    // 정리 목록에 들어가지 않는 가짜 ID라 직접 지운다. 검증이 중간에 실패해도 작업을 남기지 않게 본문이 아니라 여기서 지운다
    @AfterEach
    void cleanUpExpirationEventTasks() {
        removeTask(PaymentDelayTask.paymentExpire(COMMIT_PAYMENT_ID));
        removeTask(PaymentDelayTask.paymentExpire(ROLLBACK_PAYMENT_ID));
    }

    @Test
    @DisplayName("결제를 만든 트랜잭션이 커밋되면 리스너가 만료 시각을 점수로 PAYMENT_EXPIRE를 실제 Redis에 등록한다")
    void scheduleOnCommit() {
        // given
        LocalDateTime expiresAt = LocalDateTime.now(clock).withNano(0).plusMinutes(5);

        // when
        transactionTemplate.executeWithoutResult(status ->
            eventPublisher.publishEvent(new PaymentCreatedEvent(COMMIT_PAYMENT_ID, expiresAt)));

        // then
        assertThat(scoreOf(PaymentDelayTask.paymentExpire(COMMIT_PAYMENT_ID)))
            .isEqualTo((double) expiresAt.atZone(clock.getZone()).toInstant().toEpochMilli());
    }

    @Test
    @DisplayName("결제를 만든 트랜잭션이 롤백되면 리스너가 실행되지 않아 존재하지 않는 결제의 만료 작업이 남지 않는다")
    void noTaskOnRollback() {
        // given
        LocalDateTime expiresAt = LocalDateTime.now(clock).withNano(0).plusMinutes(5);

        // when
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new PaymentCreatedEvent(ROLLBACK_PAYMENT_ID, expiresAt));
            status.setRollbackOnly();
        });

        // then
        assertThat(scoreOf(PaymentDelayTask.paymentExpire(ROLLBACK_PAYMENT_ID))).isNull();
    }
}
