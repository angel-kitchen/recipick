package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipPaymentAttemptRepository extends JpaRepository<MembershipPaymentAttempt, Long> {

    boolean existsByBillingKeyIdAndStatusIn(Long billingKeyId, Collection<MembershipPaymentAttemptStatus> statuses);
}
