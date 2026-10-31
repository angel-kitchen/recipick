package com.recipick.domain.member.repository;

import com.recipick.domain.member.dto.MemberMembershipData;
import com.recipick.domain.member.dto.MembershipBillingTarget;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipType;
import java.time.LocalDateTime;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    @Query("""
            SELECT m FROM Membership m
            WHERE m.member.id = :memberId
              AND m.startAt <= :now AND COALESCE(m.graceEndAt, m.endAt) > :now
              AND m.activatedAt IS NOT NULL AND m.activatedAt <= :now
              AND m.terminatedAt IS NULL
            ORDER BY m.startAt DESC, m.id DESC
            """)
    List<Membership> findActiveMemberships(@Param("memberId") Long memberId,
            @Param("now") LocalDateTime now, Pageable pageable);

    // 실제 종료 시각을 고려하되 지워진 과거 유예 이력은 재현하지 않는다. 주문 적립률은 생성 시 저장한다.
    @Query("""
            SELECT m FROM Membership m
            WHERE m.member.id = :memberId
              AND m.startAt <= :at AND COALESCE(m.graceEndAt, m.endAt) > :at
              AND m.activatedAt IS NOT NULL AND m.activatedAt <= :at
              AND (m.terminatedAt IS NULL OR m.terminatedAt > :at)
            ORDER BY m.startAt DESC, m.id DESC
            """)
    List<Membership> findMembershipsAt(@Param("memberId") Long memberId,
            @Param("at") LocalDateTime at, Pageable pageable);

    @Query("""
            SELECT new com.recipick.domain.member.dto.MemberMembershipData(m.member.id, m.type, m.endAt)
            FROM Membership m
            WHERE m.member.id IN :memberIds AND m.startAt <= :now AND COALESCE(m.graceEndAt, m.endAt) > :now
              AND m.activatedAt IS NOT NULL AND m.activatedAt <= :now
              AND m.terminatedAt IS NULL
            ORDER BY m.startAt DESC, m.id DESC
            """)
    List<MemberMembershipData> findActiveMembershipsForMembers(@Param("memberIds") List<Long> memberIds,
            @Param("now") LocalDateTime now);

    @Query("SELECT DISTINCT m.member.id FROM Membership m WHERE m.member.id IN :memberIds AND m.type = :type")
    List<Long> findMemberIdsWithTypeHistory(@Param("memberIds") List<Long> memberIds,
            @Param("type") MembershipType type);

    Page<Membership> findAllByMemberId(Long memberId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Membership m WHERE m.id = :membershipId AND m.member.id = :memberId")
    Optional<Membership> findByIdAndMemberIdForUpdate(@Param("membershipId") Long membershipId,
            @Param("memberId") Long memberId);

    boolean existsByMemberId(Long memberId);

    boolean existsByMemberIdAndActivatedAtIsNotNull(Long memberId);

    @Query("""
            SELECT COUNT(m) > 0 FROM Membership m
            WHERE m.member.id = :memberId AND m.terminatedAt IS NULL
              AND ((m.activatedAt IS NOT NULL AND m.activatedAt <= :now
                    AND m.startAt <= :now AND COALESCE(m.graceEndAt, m.endAt) > :now)
                OR (m.type = com.recipick.domain.member.entity.enums.MembershipType.PAID
                    AND m.activatedAt IS NULL)
                OR (m.type = com.recipick.domain.member.entity.enums.MembershipType.TRIAL
                    AND m.autoRenew = true AND m.cancelReservedAt IS NULL AND m.endAt <= :now))
            """)
    boolean existsBlocking(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);

    @Query("""
            SELECT m FROM Membership m
            WHERE m.member.id = :memberId AND m.terminatedAt IS NULL
              AND ((m.type = com.recipick.domain.member.entity.enums.MembershipType.PAID
                    AND m.activatedAt IS NULL)
                OR (m.type = com.recipick.domain.member.entity.enums.MembershipType.TRIAL
                    AND m.autoRenew = true AND m.cancelReservedAt IS NULL AND m.endAt <= :now))
            ORDER BY m.startAt DESC, m.id DESC
            """)
    List<Membership> findPendingMemberships(@Param("memberId") Long memberId,
            @Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT m.member.id FROM Membership m WHERE m.id = :membershipId")
    Optional<Long> findMemberIdOfMembership(@Param("membershipId") Long membershipId);

    @Query("SELECT m.id FROM Membership m WHERE m.member.id = :memberId ORDER BY m.id")
    List<Long> findIdsByMemberId(@Param("memberId") Long memberId);

    @Query("""
            SELECT new com.recipick.domain.member.dto.MembershipBillingTarget(m.id, m.member.id)
            FROM Membership m
            WHERE m.type = com.recipick.domain.member.entity.enums.MembershipType.TRIAL
              AND m.terminatedAt IS NULL AND m.autoRenew = true AND m.cancelReservedAt IS NULL
              AND m.endAt <= :now AND m.id > :afterId
            ORDER BY m.id
            """)
    List<MembershipBillingTarget> findTrialConversionTargets(@Param("now") LocalDateTime now,
            @Param("afterId") Long afterId, Pageable pageable);

    @Query("""
            SELECT new com.recipick.domain.member.dto.MembershipBillingTarget(m.id, m.member.id)
            FROM Membership m
            WHERE m.type = com.recipick.domain.member.entity.enums.MembershipType.PAID
              AND m.activatedAt IS NOT NULL AND m.terminatedAt IS NULL
              AND m.autoRenew = true AND m.cancelReservedAt IS NULL
              AND m.endAt <= :now AND :now < m.graceEndAt AND m.id > :afterId
            ORDER BY m.id
            """)
    List<MembershipBillingTarget> findRenewalTargets(@Param("now") LocalDateTime now,
            @Param("afterId") Long afterId, Pageable pageable);
}
