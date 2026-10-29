package com.recipick.domain.member.repository;

import com.recipick.domain.member.dto.MemberMembershipData;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    @Query("""
            SELECT m FROM Membership m
            WHERE m.member.id = :memberId
              AND m.startAt <= :now AND m.endAt > :now
              AND m.terminatedAt IS NULL
            ORDER BY m.startAt DESC, m.id DESC
            """)
    List<Membership> findActiveMemberships(@Param("memberId") Long memberId,
            @Param("now") LocalDateTime now, Pageable pageable);

    // 과거 시점의 혜택은 현재 종료 여부가 아니라 당시 종료 여부로 판정한다.
    @Query("""
            SELECT m FROM Membership m
            WHERE m.member.id = :memberId
              AND m.startAt <= :at AND m.endAt > :at
              AND (m.terminatedAt IS NULL OR m.terminatedAt > :at)
            ORDER BY m.startAt DESC, m.id DESC
            """)
    List<Membership> findMembershipsAt(@Param("memberId") Long memberId,
            @Param("at") LocalDateTime at, Pageable pageable);

    @Query("""
            SELECT new com.recipick.domain.member.dto.MemberMembershipData(m.member.id, m.type, m.endAt)
            FROM Membership m
            WHERE m.member.id IN :memberIds AND m.startAt <= :now AND m.endAt > :now
              AND m.terminatedAt IS NULL
            ORDER BY m.startAt DESC, m.id DESC
            """)
    List<MemberMembershipData> findActiveMembershipsForMembers(@Param("memberIds") List<Long> memberIds,
            @Param("now") LocalDateTime now);

    @Query("SELECT DISTINCT m.member.id FROM Membership m WHERE m.member.id IN :memberIds AND m.type = :type")
    List<Long> findMemberIdsWithTypeHistory(@Param("memberIds") List<Long> memberIds,
            @Param("type") MembershipType type);

    Page<Membership> findAllByMemberId(Long memberId, Pageable pageable);

    boolean existsByMemberId(Long memberId);
}
