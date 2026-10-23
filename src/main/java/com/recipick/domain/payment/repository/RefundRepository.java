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

    // 주문 결제 조회(1-2)용 단건
    List<Refund> findAllByPaymentIdOrderByIdDesc(Long paymentId);

    // 결제 내역(1-3)용 배치. 결제별 묶음과 최신 판정은 Service가 하므로 정렬은 두지 않는다
    List<Refund> findAllByPaymentIdIn(Collection<Long> paymentIds);
}
