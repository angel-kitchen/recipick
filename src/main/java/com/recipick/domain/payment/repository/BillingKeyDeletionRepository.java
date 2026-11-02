package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.BillingKeyDeletion;
import com.recipick.domain.payment.entity.enums.BillingKeyDeletionStatus;
import com.recipick.domain.payment.service.BillingKeyDeletionRef;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingKeyDeletionRepository extends JpaRepository<BillingKeyDeletion, Long> {
    @Query("SELECT d.id FROM BillingKeyDeletion d WHERE d.billingKey.id = :keyId")
    Optional<Long> findIdByBillingKeyId(@Param("keyId") Long keyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM BillingKeyDeletion d WHERE d.id = :id")
    Optional<BillingKeyDeletion> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT new com.recipick.domain.payment.service.BillingKeyDeletionRef(d.id, d.billingKey.id,
            d.billingKey.memberId)
          FROM BillingKeyDeletion d WHERE d.id = :id
        """)
    Optional<BillingKeyDeletionRef> findRef(@Param("id") Long id);

    @Query("""
        SELECT d.id FROM BillingKeyDeletion d
         WHERE d.status IN :statuses AND d.nextCheckAt <= :now
           AND (d.leaseExpiresAt IS NULL OR d.leaseExpiresAt <= :now)
         ORDER BY d.nextCheckAt, d.id
        """)
    List<Long> findDueIds(@Param("statuses") Collection<BillingKeyDeletionStatus> statuses,
                          @Param("now") LocalDateTime now, Pageable pageable);

    @Query("""
        SELECT COUNT(d) > 0 FROM BillingKeyDeletion d
         WHERE d.billingKey.memberId = :memberId
           AND d.status = com.recipick.domain.payment.entity.enums.BillingKeyDeletionStatus.REVIEW_REQUIRED
        """)
    boolean hasReviewForMember(@Param("memberId") Long memberId);
}
