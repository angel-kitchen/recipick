package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.MembershipPayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipPaymentRepository extends JpaRepository<MembershipPayment, Long> {
}
