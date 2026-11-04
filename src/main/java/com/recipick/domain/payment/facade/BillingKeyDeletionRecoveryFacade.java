package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.config.BillingKeyDeletionProperties;
import com.recipick.domain.payment.entity.enums.BillingKeyDeletionStatus;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.repository.BillingKeyCleanupQueryRepository;
import com.recipick.domain.payment.repository.BillingKeyDeletionRepository;
import com.recipick.domain.payment.service.BillingKeyCleanupCandidate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillingKeyDeletionRecoveryFacade {
    public static final int SCAN_SIZE = 100;
    public static final int EXECUTION_SIZE = 20;
    private final BillingKeyDeletionProperties properties;
    private final BillingKeyCleanupQueryRepository keys;
    private final BillingKeyDeletionRepository deletions;
    private final BillingKeyDeletionRequestProcessor requests;
    private final BillingKeyDeletionExecutionFacade execution;
    private final Clock clock;

    // 상한 ID를 고정하고 keyset으로 끝까지 스캔한다. 낮은 ID의 보류 키가 뒤의 키를 굶기지 않는다.
    @Transactional(propagation = Propagation.NEVER)
    public int requestReplacedOnce() {
        if (!properties.enabled()) {
            return 0;
        }
        long maxId = keys.maxId();
        long cursor = 0;
        int accepted = 0;
        while (cursor < maxId) {
            List<BillingKeyCleanupCandidate> page = keys.findCandidates(cursor, maxId,
                List.of(BillingKeyStatus.REPLACED, BillingKeyStatus.REVOKED, BillingKeyStatus.DELETE_REQUESTED),
                PageRequest.of(0, SCAN_SIZE));
            if (page.isEmpty()) {
                break;
            }
            for (BillingKeyCleanupCandidate candidate : page) {
                cursor = candidate.billingKeyId();
                try {
                    if (requests.requestReplaced(candidate.memberId(), candidate.billingKeyId()) != null) {
                        accepted++;
                    }
                } catch (RuntimeException e) {
                    PaymentTaskFailureLogger.log(log, e, "빌링키 정리 접수 실패: billingKeyId={}, cause={}", candidate.billingKeyId(),
                        e.getClass().getSimpleName());
                }
            }
        }
        return accepted;
    }

    // 외부 호출은 최대 20건. 실패 건은 별도 TX에서 다음 시각으로 옮겨 다음 배치의 진행을 막지 않는다.
    @Transactional(propagation = Propagation.NEVER)
    public int recoverOnce() {
        if (!properties.enabled()) {
            return 0;
        }
        List<Long> ids = deletions.findDueIds(
            List.of(BillingKeyDeletionStatus.REQUESTED, BillingKeyDeletionStatus.REVIEW_REQUIRED),
            LocalDateTime.now(clock), PageRequest.of(0, EXECUTION_SIZE));
        int attempted = 0;
        for (Long id : ids) {
            try {
                execution.execute(id);
                attempted++;
            } catch (RuntimeException e) {
                // 임대가 끝난 뒤 동일 DB 요청을 다시 집는다. 새 요청·새 키를 만들지 않는다.
                PaymentTaskFailureLogger.log(log, e, "빌링키 삭제 실행 실패: deletionId={}, cause={}", id, e.getClass().getSimpleName());
            }
        }
        return attempted;
    }
}
