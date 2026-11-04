package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.config.MembershipBillingProperties;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.repository.MembershipRefundRepository;
import com.recipick.domain.payment.service.MembershipRefundExecutionResult;
import com.recipick.domain.payment.service.MembershipRefundRef;
import com.recipick.domain.payment.service.PaymentDelayQueue;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.domain.payment.service.WebhookEventService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 멤버십 환불 복구. 플래그와 무관하게 돈다 — 이미 접수된 환불의 실행·정합화는 멈추지 않는다.
// recoverOnce: REQUESTED이고 다음 확인 시각이 지났고(없음 포함) 임대가 없거나 끝났고 운영 확인 표시가 없는 행을 ID 순(keyset)으로 읽어
//              실행기에 넘긴다. Redis 큐 예약이 유실돼도 이 스캔이 받는다. 건별 격리 — 한 건의 예외가 다음 건을 막지 않는다.
// scheduleRequestedOf: 결제 결과 반영이 SYSTEM 환불을 요청(또는 재확인)한 뒤 커밋 밖에서 큐에 예약한다(실패는 로그만).
// reopenPendingCancelWebhooks: 환불 실행 기능이 없던 때 "환불 정합화 대기" 사유로 운영 확인에 보존된 취소 웹훅을 재처리 대기로 되돌린다.
// 비트랜잭션 진입점(Propagation.NEVER)
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipRefundRecoveryFacade {

    private final MembershipBillingProperties properties;
    private final MembershipRefundRepository refunds;
    private final MembershipRefundExecutionFacade executionFacade;
    private final PaymentDelayQueue paymentDelayQueue;
    private final WebhookEventService webhookEventService;
    private final Clock clock;

    // 처리한 환불 수
    @Transactional(propagation = Propagation.NEVER)
    public int recoverOnce() {
        LocalDateTime now = LocalDateTime.now(clock);
        long upperId = refunds.findMaxId();
        int processed = 0;
        long afterId = 0;
        Pageable pageable = PageRequest.of(0, properties.batchSize());
        while (true) {
            List<Long> ids = refunds.findRequestedForRecovery(now, afterId, upperId, pageable);
            for (Long refundId : ids) {
                processed++;
                try {
                    MembershipRefundExecutionResult result = executionFacade.execute(refundId);
                    log.info("멤버십 환불 복구 실행: refundId={}, result={}", refundId, result);
                } catch (RuntimeException e) {
                    PaymentTaskFailureLogger.log(log, e, "멤버십 환불 복구 실패(다음 실행에서 재시도): refundId={}, cause={}", refundId,
                        e.getClass().getSimpleName());
                }
            }
            if (ids.size() < properties.batchSize()) {
                return processed;
            }
            afterId = ids.get(ids.size() - 1);
        }
    }

    // 그 시도의 REQUESTED 환불(운영 확인 표시 없음)을 지금 실행하도록 큐에 넣는다. 예약 실패는 로그만 — DB 복구가 받는다
    public void scheduleRequestedOf(Long attemptId) {
        try {
            for (Long refundId : refunds.findIdsByMembershipPaymentAttemptIdOrderByIdAsc(attemptId)) {
                MembershipRefundRef ref = refunds.findRef(refundId).orElse(null);
                if (ref == null || ref.status() != MembershipRefundStatus.REQUESTED || ref.opsReviewReason() != null) {
                    continue;
                }
                paymentDelayQueue.enqueueIfEarlier(PaymentDelayTask.membershipRefundExecute(refundId),
                    clock.instant());
            }
        } catch (RuntimeException e) {
            PaymentTaskFailureLogger.log(log, e, "SYSTEM 환불 실행 예약 실패(DB 복구가 받는다): attemptId={}, cause={}", attemptId,
                e.getClass().getSimpleName());
        }
    }

    // 되돌린 웹훅 수. MANUAL_REVIEW 중 사유가 "환불 정합화 대기"인 이벤트만 RETRY_PENDING으로 되돌리고 바로 처리하도록 큐에 넣는다
    @Transactional(propagation = Propagation.NEVER)
    public int reopenPendingCancelWebhooks() {
        LocalDateTime now = LocalDateTime.now(clock);
        int reopened = 0;
        long afterId = 0;
        Pageable pageable = PageRequest.of(0, properties.batchSize());
        while (true) {
            List<Long> ids = webhookEventService.findManualReviewIdsByReason(
                MembershipWebhookReconciliationFacade.REASON_REFUND_RECONCILIATION_PENDING, afterId, pageable);
            for (Long eventId : ids) {
                try {
                    if (webhookEventService.reopenForRetry(eventId,
                        MembershipWebhookReconciliationFacade.REASON_REFUND_RECONCILIATION_PENDING, now)) {
                        reopened++;
                        paymentDelayQueue.enqueueIfEarlier(PaymentDelayTask.webhookProcess(eventId), clock.instant());
                    }
                } catch (RuntimeException e) {
                    PaymentTaskFailureLogger.log(log, e, "보류 취소 웹훅 재개 실패(다음 실행에서 재시도): webhookEventId={}, cause={}", eventId,
                        e.getClass().getSimpleName());
                }
            }
            if (ids.size() < properties.batchSize()) {
                return reopened;
            }
            afterId = ids.get(ids.size() - 1);
        }
    }
}
