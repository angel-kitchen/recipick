package com.recipick.domain.payment.facade;

import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.payment.config.BillingKeyDeletionProperties;
import com.recipick.domain.payment.entity.BillingKey;
import com.recipick.domain.payment.entity.BillingKeyDeletion;
import com.recipick.domain.payment.entity.enums.BillingKeyDeletionStatus;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.repository.BillingKeyCleanupQueryRepository;
import com.recipick.domain.payment.repository.BillingKeyDeletionRepository;
import com.recipick.domain.payment.repository.BillingKeyRepository;
import com.recipick.domain.payment.service.BillingKeyDeletionCode;
import com.recipick.domain.payment.service.BillingKeyDeletionRef;
import com.recipick.domain.payment.service.BillingKeyDeletionTicket;
import com.recipick.infra.portone.dto.PortOneBillingKeyDeletionObservation;
import com.recipick.infra.portone.dto.PortOneBillingKeyDeletionObservation.Kind;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 어떤 쓰기 경로든 첫 DB 작업은 회원 정산 잠금이다. MEMBER → BILLING_KEY → BILLING_KEY_DELETION 순서다.
@Component
@RequiredArgsConstructor
public class BillingKeyDeletionProcessor {
    private final MemberService members;
    private final BillingKeyRepository keys;
    private final BillingKeyDeletionRepository deletions;
    private final BillingKeyCleanupQueryRepository query;
    private final BillingKeyDeletionProperties properties;
    private final Clock clock;

    @Transactional
    public Optional<BillingKeyDeletionTicket> claim(BillingKeyDeletionRef ref) {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        Locked locked = lock(ref);
        BillingKeyDeletion task = locked.task();
        LocalDateTime now = LocalDateTime.now(clock);
        if (task.getStatus() == BillingKeyDeletionStatus.COMPLETED) {
            return Optional.empty();
        }
        if (locked.key().getStatus() == BillingKeyStatus.DELETED && locked.key().getDeletedAt() != null) {
            task.complete(now, BillingKeyDeletionCode.DELETED);
            return Optional.empty();
        }
        if (locked.key().getStatus() != BillingKeyStatus.DELETE_REQUESTED) {
            task.review(BillingKeyDeletionCode.STATE_CONFLICT, now);
            return Optional.empty();
        }
        if (!task.claim(now)) {
            return Optional.empty();
        }
        // 미해결 시도가 뒤늦게 발견되면 DELETE를 멈추고 조회만 계속한다.
        if (unsafeUse(ref.billingKeyId())) {
            task.review(BillingKeyDeletionCode.UNRESOLVED_ATTEMPT, now);
            return Optional.empty();
        }
        return Optional.of(ticket(locked));
    }

    @Transactional
    public boolean recordVerifiedIdentity(BillingKeyDeletionTicket ticket) {
        Locked locked = lock(ref(ticket));
        verifyTicket(locked, ticket);
        if (locked.task().getStatus() == BillingKeyDeletionStatus.COMPLETED) {
            return false;
        }
        locked.task().recordVerifiedIdentity(LocalDateTime.now(clock));
        return true;
    }

    @Transactional
    public boolean prepareDelete(BillingKeyDeletionTicket ticket) {
        Locked locked = lock(ref(ticket));
        verifyTicket(locked, ticket);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!properties.enabled() || ticket.observationOnly()
            || !locked.task().holdsClaim(ticket.token(), now)) {
            return false;
        }
        if (locked.key().getStatus() != BillingKeyStatus.DELETE_REQUESTED || unsafeUse(ticket.billingKeyId())) {
            locked.task().review(BillingKeyDeletionCode.UNRESOLVED_ATTEMPT, now);
            return false;
        }
        if (locked.task().getIdentityVerifiedAt() == null) {
            locked.task().review(BillingKeyDeletionCode.SCOPE_UNPROVEN, now);
            return false;
        }
        return locked.task().prepareDelete(ticket.token(), now);
    }

    // 토큰이 지난 결과라도 동일 키·동일 상점의 확정된 삭제 사실은 원자적으로 반영한다.
    @Transactional
    public boolean confirmAbsent(BillingKeyDeletionTicket ticket, PortOneBillingKeyDeletionObservation observed) {
        if (observed.kind() != Kind.ABSENT) {
            throw new IllegalArgumentException("삭제 확인 관측이 필요합니다");
        }
        Locked locked = lock(ref(ticket));
        verifyTicket(locked, ticket);
        if (locked.key().getStatus() != BillingKeyStatus.DELETE_REQUESTED
            && locked.key().getStatus() != BillingKeyStatus.DELETED) {
            locked.task().review(BillingKeyDeletionCode.STATE_CONFLICT, LocalDateTime.now(clock));
            return false;
        }
        BillingKeyDeletionCode evidence = BillingKeyDeletionCode.fromPortOne(observed.code());
        // 현재 설정을 과거 발급 상점이라고 추정하지 않는다. 최초 GET이 404뿐이면 잘못된 상점 가능성을 배제할 수 없다.
        if (evidence == BillingKeyDeletionCode.BILLING_KEY_NOT_FOUND && locked.task().getIdentityVerifiedAt() == null) {
            locked.task().defer(ticket.token(), LocalDateTime.now(clock), BillingKeyDeletionCode.SCOPE_UNPROVEN);
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        locked.task().recordVerifiedIdentity(now);
        locked.key().confirmRemoteDeletion(now);
        locked.task().complete(now, evidence);
        return true;
    }

    @Transactional
    public boolean defer(BillingKeyDeletionTicket ticket, BillingKeyDeletionCode code) {
        Locked locked = lock(ref(ticket));
        verifyTicket(locked, ticket);
        return locked.task().defer(ticket.token(), LocalDateTime.now(clock), code);
    }

    private boolean unsafeUse(Long keyId) {
        return query.hasUnresolvedUse(keyId, BillingKeyDeletionRequestProcessor.UNRESOLVED,
            MembershipOpsReviewReason.BLOCKING);
    }

    private Locked lock(BillingKeyDeletionRef ref) {
        members.lockMemberForSettlement(ref.memberId());
        BillingKey key = keys.findByIdForUpdate(ref.billingKeyId())
            .orElseThrow(() -> new IllegalStateException("삭제 대상 키가 없음: billingKeyId=" + ref.billingKeyId()));
        if (!ref.memberId().equals(key.getMemberId())) {
            throw new IllegalStateException("삭제 대상 키의 소유 관계 불일치");
        }
        BillingKeyDeletion task = deletions.findByIdForUpdate(ref.deletionId())
            .orElseThrow(() -> new IllegalStateException("삭제 요청이 없음: deletionId=" + ref.deletionId()));
        if (!task.getBillingKey().getId().equals(key.getId())) {
            throw new IllegalStateException("삭제 요청과 빌링키의 연결 불일치");
        }
        return new Locked(key, task);
    }

    private static void verifyTicket(Locked locked, BillingKeyDeletionTicket ticket) {
        if (!locked.key().getPortoneBillingKey().equals(ticket.billingKey())
            || !locked.task().getStoreId().equals(ticket.storeId())
            || locked.task().getReason() != ticket.reason()) {
            throw new IllegalStateException("삭제 실행의 고정 대상이 변경되었습니다");
        }
    }

    private static BillingKeyDeletionRef ref(BillingKeyDeletionTicket ticket) {
        return new BillingKeyDeletionRef(ticket.deletionId(), ticket.billingKeyId(), ticket.memberId());
    }

    private static BillingKeyDeletionTicket ticket(Locked locked) {
        BillingKeyDeletion task = locked.task();
        BillingKey key = locked.key();
        return new BillingKeyDeletionTicket(task.getId(), key.getId(), key.getMemberId(), key.getPortoneBillingKey(),
            task.getStoreId(), task.getReason(), task.getClaimToken(), task.getRequestedAt(),
            task.getStatus() == BillingKeyDeletionStatus.REVIEW_REQUIRED);
    }

    private record Locked(BillingKey key, BillingKeyDeletion task) {
    }
}
