package com.recipick.domain.member.repository;

import com.recipick.domain.member.entity.MemberAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberAddressRepository extends JpaRepository<MemberAddress, Long> {

    @Query("""
            SELECT a FROM MemberAddress a
            WHERE a.member.id = :memberId
            ORDER BY CASE WHEN a.id = :selectedAddressId THEN 0 ELSE 1 END,
                     a.createdAt DESC, a.id DESC
            """)
    List<MemberAddress> findMemberAddresses(@Param("memberId") Long memberId,
            @Param("selectedAddressId") Long selectedAddressId);

    Optional<MemberAddress> findByIdAndMemberId(Long id, Long memberId);

    long countByMemberId(Long memberId);
}
