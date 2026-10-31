package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipPaymentRepository extends JpaRepository<MembershipPayment, Long> {

    boolean existsByMembershipIdInAndStatusIn(Collection<Long> membershipIds,
                                              Collection<MembershipPaymentStatus> statuses);
}
