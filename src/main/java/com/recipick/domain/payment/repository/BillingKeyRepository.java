package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.BillingKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingKeyRepository extends JpaRepository<BillingKey, Long> {
}
