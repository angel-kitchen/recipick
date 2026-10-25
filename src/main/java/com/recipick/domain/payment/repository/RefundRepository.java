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
}
