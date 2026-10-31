package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipPaymentAttemptRepository extends JpaRepository<MembershipPaymentAttempt, Long> {

    // 연관 경로(membershipPayment.membershipId)를 타는 파생 쿼리
    boolean existsByMembershipPaymentMembershipIdInAndStatusIn(Collection<Long> membershipIds,
                                                               Collection<MembershipPaymentAttemptStatus> statuses);

    boolean existsByBillingKeyIdAndStatusIn(Long billingKeyId, Collection<MembershipPaymentAttemptStatus> statuses);

    // PAID인데 돌려줘야 하는 시도 중 그 시도의 SYSTEM 환불이 COMPLETED가 아닌 것.
    // MEMBER 환불 COMPLETED는 해제하지 않는다 — 돌려줘야 할 돈은 SYSTEM 환불로만 돌아간다
    @Query("""
        SELECT COUNT(a) > 0
          FROM MembershipPaymentAttempt a
         WHERE a.membershipPayment.membershipId IN :membershipIds
           AND a.status = com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus.PAID
           AND a.refundRequiredAt IS NOT NULL
           AND NOT EXISTS (
               SELECT 1 FROM MembershipRefund r
                WHERE r.membershipPaymentAttempt = a
                  AND r.requestedBy = com.recipick.domain.payment.entity.enums.RefundRequester.SYSTEM
                  AND r.status = com.recipick.domain.payment.entity.enums.MembershipRefundStatus.COMPLETED)
        """)
    boolean existsPaidRequiringRefundWithoutCompletedSystemRefund(
        @Param("membershipIds") Collection<Long> membershipIds);
}
