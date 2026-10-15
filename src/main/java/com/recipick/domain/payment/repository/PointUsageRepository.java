package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.PointUsage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointUsageRepository extends JpaRepository<PointUsage, Long> {
}
