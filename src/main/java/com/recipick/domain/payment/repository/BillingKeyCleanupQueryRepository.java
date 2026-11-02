package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.BillingKey;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.service.BillingKeyCleanupCandidate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// 기존 등록 Repository를 덮어쓰지 않는다. 결제 도메인 엔티티만 조회한다.
public interface BillingKeyCleanupQueryRepository extends Repository<BillingKey, Long> {
    @Query("SELECT COALESCE(MAX(k.id), 0) FROM BillingKey k")
    long maxId();

    @Query("""
        SELECT new com.recipick.domain.payment.service.BillingKeyCleanupCandidate(k.id, k.memberId)
          FROM BillingKey k
         WHERE k.id > :afterId AND k.id <= :maxId AND k.status IN :statuses
           AND NOT EXISTS (SELECT d.id FROM BillingKeyDeletion d WHERE d.billingKey = k)
         ORDER BY k.id
        """)
    List<BillingKeyCleanupCandidate> findCandidates(@Param("afterId") long afterId,
                                                    @Param("maxId") long maxId, @Param("statuses") Collection<BillingKeyStatus> statuses, Pageable pageable);

    @Query("SELECT k.id FROM BillingKey k WHERE k.memberId = :memberId ORDER BY k.id")
    List<Long> findIdsOfMember(@Param("memberId") Long memberId);

    @Query("""
        SELECT COUNT(k) > 0 FROM BillingKey k
         WHERE k.memberId = :memberId
           AND (k.status IS NULL
                OR k.status <> com.recipick.domain.payment.entity.enums.BillingKeyStatus.DELETED
                OR k.deletedAt IS NULL
                OR EXISTS (SELECT d.id FROM BillingKeyDeletion d WHERE d.billingKey = k
                    AND d.status <> com.recipick.domain.payment.entity.enums.BillingKeyDeletionStatus.COMPLETED))
        """)
    boolean hasNotConfirmedDeleted(@Param("memberId") Long memberId);

    @Query("""
        SELECT COUNT(a) > 0 FROM MembershipPaymentAttempt a
         WHERE a.billingKey.id = :keyId
           AND (a.status IN :statuses OR a.opsReviewReason IN :blocking
             OR EXISTS (SELECT b.id FROM MembershipPaymentAttempt b
                         WHERE b.membershipPayment = a.membershipPayment AND b.opsReviewReason IN :blocking))
        """)
    boolean hasUnresolvedUse(@Param("keyId") Long keyId,
                             @Param("statuses") Collection<MembershipPaymentAttemptStatus> statuses,
                             @Param("blocking") Collection<MembershipOpsReviewReason> blocking);
}
