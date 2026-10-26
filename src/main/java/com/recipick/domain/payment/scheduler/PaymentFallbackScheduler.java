package com.recipick.domain.payment.scheduler;

import com.recipick.domain.payment.facade.PaymentReconciliationFacade;
import com.recipick.domain.payment.facade.RefundReconciliationFacade;
import com.recipick.domain.payment.service.PaymentDelayQueue;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.payment.service.WebhookEventService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

// 지연 큐 유실 복구용 저빈도 Sweep. MySQL이 원본이고 Redis는 실행 신호일 뿐이라, 큐에서 사라진 작업을 DB 상태로 다시 등록한다.
// 처리는 하지 않는다 — PortOne도 Processor도 부르지 않고 큐에 넣기만 해서 처리 경로를 Worker 하나로 유지한다.
// 정상 처리 중인 작업과 겹치지 않도록 각 상태가 정상이라면 이미 실행됐어야 할 만큼 오래된 행만 고른다
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFallbackScheduler {

    // 한 번에 읽는 ID 수. ID만 읽으므로 크게 잡아도 가볍다
    static final int BATCH_SIZE = 100;

    // 방금 저장돼 수신 Facade가 큐에 넣고 있는 이벤트와 겹치지 않게 둔다
    static final Duration RECEIVED_GRACE = Duration.ofMinutes(1);

    // 웹훅 재시도 간격은 최대 8분이다. 이보다 오래 RETRY_PENDING이면 예약이 사라진 것으로 본다
    static final Duration RETRY_RECOVERY_AGE = Duration.ofMinutes(10);

    private final WebhookEventService webhookEventService;
    private final PaymentService paymentService;
    private final PaymentDelayQueue paymentDelayQueue;
    private final Clock clock;

    // 한 번 훑어 큐에 다시 넣는다. 주기 실행(1시간)은 PaymentScheduledTasks가 맡고, 테스트는 이 메서드를 직접 부른다
    public void sweep() {
        LocalDateTime now = LocalDateTime.now(clock);
        Instant executeAt = clock.instant();
        try {
            int webhookEvents = recoverWebhookEvents(now, executeAt);
            int refunds = recoverRefunds(now, executeAt);
            if (webhookEvents + refunds > 0) {
                log.warn("Fallback Sweep 재등록: webhookEvents={}, refunds={}", webhookEvents, refunds);
            }
        } catch (DataAccessException e) {
            // DB·Redis 장애면 남은 건도 같은 이유로 실패한다. 한 번만 남기고 다음 주기에 다시 한다
            log.error("Fallback Sweep 중단", e);
        }
    }

    // RECEIVED(큐 등록 실패), RETRY_PENDING(재예약 실패), 오래된 PROCESSING(Worker가 처리 중 죽음)
    private int recoverWebhookEvents(LocalDateTime now, Instant executeAt) {
        LocalDateTime receivedBefore = now.minus(RECEIVED_GRACE);
        LocalDateTime retryBefore = now.minus(RETRY_RECOVERY_AGE);
        LocalDateTime processingBefore = now.minus(PaymentReconciliationFacade.PROCESSING_TIMEOUT);
        int count = 0;
        long afterId = 0L;
        while (true) {
            List<Long> ids = webhookEventService.findRecoverableIds(
                receivedBefore, retryBefore, processingBefore, afterId, BATCH_SIZE);
            for (Long id : ids) {
                paymentDelayQueue.enqueueIfEarlier(PaymentDelayTask.webhookProcess(id), executeAt);
            }
            count += ids.size();
            if (ids.size() < BATCH_SIZE) {
                return count;
            }
            afterId = ids.get(ids.size() - 1);
        }
    }

    // 회원 환불 REQUESTED. 첫 재확인 시각이 지난 것만 넣는다. 고빈도 재확인 중인 환불의 예약을 최대 한 번 앞당길 수 있지만
    // 재확인은 조회뿐이고 apply가 잠금 아래에서 멱등이라 결과는 같다
    private int recoverRefunds(LocalDateTime now, Instant executeAt) {
        LocalDateTime requestedBefore = now.minus(RefundReconciliationFacade.INITIAL_DELAY);
        int count = 0;
        long afterId = 0L;
        while (true) {
            List<Long> ids = paymentService.findStaleMemberRefundIds(requestedBefore, afterId, BATCH_SIZE);
            for (Long id : ids) {
                paymentDelayQueue.enqueueIfEarlier(PaymentDelayTask.refundReconcile(id), executeAt);
            }
            count += ids.size();
            if (ids.size() < BATCH_SIZE) {
                return count;
            }
            afterId = ids.get(ids.size() - 1);
        }
    }
}
