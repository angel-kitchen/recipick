package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.Refund;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    @Query("""

        SELECT r
            FROM Refund r
            WHERE r.payment.id = :paymentId
              AND r.requestedBy =
                  com.recipick.domain.payment.entity.enums.RefundRequester.SYSTEM
            """)
    Optional<Refund> findSystemRefundByPaymentId(@Param("paymentId") Long paymentId);

    // 결제 후 취소의 같은 키 재요청. idempotency_key가 UNIQUE라 최대 한 건이다
    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    // 주문 결제 조회용 단건
    List<Refund> findAllByPaymentIdOrderByIdDesc(Long paymentId);

    // 결제 내역용 배치. 결제별 묶음과 최신 판정은 Service가 하므로 정렬은 두지 않는다
    List<Refund> findAllByPaymentIdIn(Collection<Long> paymentIds);

    // 웹훅·재조회가 PortOne 취소 ID로 환불을 찾는다. portone_cancellation_id가 UNIQUE라 최대 한 건이다
    Optional<Refund> findByPortoneCancellationId(String portoneCancellationId);

    // 취소 ID를 아직 못 받은 회원 환불을 찾는 2차 경로. 정합화는 정확히 한 건일 때만 자동 반영하므로 목록으로 돌려준다
    @Query("""
        SELECT r
            FROM Refund r
            WHERE r.payment.id = :paymentId
              AND r.requestedBy =
                  com.recipick.domain.payment.entity.enums.RefundRequester.MEMBER
              AND r.status =
                  com.recipick.domain.payment.entity.enums.RefundStatus.REQUESTED
            """)
    List<Refund> findRequestedMemberRefundsByPaymentId(@Param("paymentId") Long paymentId);
}
