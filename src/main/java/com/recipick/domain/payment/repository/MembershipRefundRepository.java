package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.MembershipRefund;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRefundRepository extends JpaRepository<MembershipRefund, Long> {
}
