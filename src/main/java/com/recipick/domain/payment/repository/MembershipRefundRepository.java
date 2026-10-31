package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRefundRepository extends JpaRepository<MembershipRefund, Long> {

    // 환불 결과를 기다리는 중
    boolean existsByMembershipPaymentMembershipIdInAndStatus(Collection<Long> membershipIds,
                                                             MembershipRefundStatus status);
}
