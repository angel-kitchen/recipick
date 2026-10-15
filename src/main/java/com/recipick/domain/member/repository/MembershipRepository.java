package com.recipick.domain.member.repository;

import com.recipick.domain.member.entity.Membership;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, Long> {
}
