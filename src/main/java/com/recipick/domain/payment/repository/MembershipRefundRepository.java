package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import java.util.Collection;
import java.util.List;
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

    // TX2의 SYSTEM 환불 멱등 판정. 같은 시도의 SYSTEM 환불 행을 상태와 무관하게 모두 본다 — REQUESTED만 보면 FAILED·COMPLETED 뒤에
    // 새 요청을 또 만든다. 시도 PK 잠금 아래에서 본다(환불 생성은 모두 같은 잠금 순서 아래)
    List<MembershipRefund> findByMembershipPaymentAttemptIdAndRequestedByOrderByIdAsc(Long membershipPaymentAttemptId,
                                                                                      RefundRequester requestedBy);
}
