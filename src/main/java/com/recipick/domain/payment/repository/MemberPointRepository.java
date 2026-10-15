package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.MemberPoint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberPointRepository extends JpaRepository<MemberPoint, Long> {
}
