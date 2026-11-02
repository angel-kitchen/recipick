package com.recipick.domain.payment.facade;

import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.payment.config.BillingKeyDeletionProperties;
import com.recipick.domain.payment.entity.BillingKey;
import com.recipick.domain.payment.entity.BillingKeyDeletion;
import com.recipick.domain.payment.entity.enums.BillingKeyDeletionReason;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.exception.BillingKeyDeletionErrorCode;
import com.recipick.domain.payment.repository.BillingKeyCleanupQueryRepository;
import com.recipick.domain.payment.repository.BillingKeyDeletionRepository;
import com.recipick.domain.payment.repository.BillingKeyRepository;
import com.recipick.domain.payment.service.BillingKeyDeletionCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.portone.PortOneProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BillingKeyDeletionRequestProcessor {
    public static final Set<MembershipPaymentAttemptStatus> UNRESOLVED = Set.of(
        MembershipPaymentAttemptStatus.READY, MembershipPaymentAttemptStatus.DISPATCHING,
        MembershipPaymentAttemptStatus.UNKNOWN, MembershipPaymentAttemptStatus.MANUAL_REVIEW);

    private final MemberService members;
    private final BillingKeyRepository keys;
    private final BillingKeyCleanupQueryRepository query;
    private final BillingKeyDeletionRepository deletions;
    private final BillingKeyDeletionProperties properties;
    private final PortOneProperties portOne;
    private final Clock clock;

    // ID는 호출자가 TX 밖에서 찾는다. 첫 DB 작업은 언제나 회원 잠금이다.
    @Transactional
    public Long requestReplaced(Long memberId, Long keyId) {
        if (!properties.enabled()) {
            return null;
        }
        members.lockMemberForSettlement(memberId);
        BillingKey key = lockOwned(memberId, keyId);
        if (key.getStatus() != BillingKeyStatus.REPLACED && key.getStatus() != BillingKeyStatus.REVOKED
            && key.getStatus() != BillingKeyStatus.DELETE_REQUESTED) {
            return null; // 재활성화가 먼저 커밋한 키는 지우지 않는다.
        }
        return requestLocked(key, key.getStatus() == BillingKeyStatus.REVOKED
            ? BillingKeyDeletionReason.REVOKED_KEY : BillingKeyDeletionReason.REPLACED_KEY);
    }

    // 탈퇴 준비 호출자는 회원 잠금을 이미 보유한다. 아직 PG를 부르지 않는다.
    @Transactional(propagation = Propagation.MANDATORY)
    public Long requestForWithdrawal(Long memberId, Long keyId) {
        if (!properties.withdrawalReady()) {
            throw new IllegalStateException("탈퇴 결제수단 준비 기능이 비활성 상태입니다");
        }
        return requestLocked(lockOwned(memberId, keyId), BillingKeyDeletionReason.WITHDRAWAL);
    }

    private BillingKey lockOwned(Long memberId, Long keyId) {
        BillingKey key = keys.findByIdForUpdate(keyId)
            .orElseThrow(() -> new IllegalStateException("삭제할 키 행이 없음: billingKeyId=" + keyId));
        if (!memberId.equals(key.getMemberId())) {
            throw new IllegalStateException("빌링키 소유 회원 불일치: billingKeyId=" + keyId);
        }
        return key;
    }

    private Long requestLocked(BillingKey key, BillingKeyDeletionReason reason) {
        if (key.getStatus() == BillingKeyStatus.DELETED) {
            if (key.getDeletedAt() == null) {
                throw new IllegalStateException("삭제 확인 시각이 없음: billingKeyId=" + key.getId());
            }
            return deletions.findIdByBillingKeyId(key.getId()).orElse(null);
        }
        Long existing = deletions.findIdByBillingKeyId(key.getId()).orElse(null);
        if (existing != null) {
            return existing; // 재접수로 요청 시각·상점·재시도 창을 바꾸지 않는다.
        }
        if (query.hasUnresolvedUse(key.getId(), UNRESOLVED, MembershipOpsReviewReason.BLOCKING)) {
            return null;
        }
        String storeId = portOne.storeId();
        if (storeId == null || storeId.isBlank() || storeId.length() > 100) {
            throw new BusinessException(BillingKeyDeletionErrorCode.CLEANUP_CONFIG_ERROR);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        boolean orphan = key.getStatus() == BillingKeyStatus.DELETE_REQUESTED;
        key.requestDeletion(now);
        BillingKeyDeletion task = BillingKeyDeletion.requested(key, storeId, reason, now);
        if (orphan) {
            // 과거 DELETE 발송 여부를 추정하지 않는다. 조회로만 정합화한다.
            task.review(BillingKeyDeletionCode.STATE_CONFLICT, now);
        }
        return deletions.saveAndFlush(task).getId();
    }
}
