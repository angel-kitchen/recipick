package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByPaymentIdAndRequestedBy(Long paymentId, RefundRequester requestedBy);
}
