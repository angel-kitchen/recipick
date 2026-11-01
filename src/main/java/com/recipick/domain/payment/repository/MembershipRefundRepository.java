package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRefundRepository extends JpaRepository<MembershipRefund, Long> {

    // 환불 결과를 기다리는 중. 회차 직접 참조(membership_payment_id)는 NULL을 허용하므로 쓰지 않고,
    // 항상 채워지는 시도 FK(membership_payment_attempt_id) → 그 시도의 회차 → 이용권 경로로 본다
    @Query("""
        SELECT COUNT(r) > 0
          FROM MembershipRefund r
         WHERE r.membershipPaymentAttempt.membershipPayment.membershipId IN :membershipIds
           AND r.status = :status
        """)
    boolean existsByMembershipPaymentMembershipIdInAndStatus(@Param("membershipIds") Collection<Long> membershipIds,
                                                             @Param("status") MembershipRefundStatus status);
}
