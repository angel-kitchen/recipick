package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.Refund;
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
}
