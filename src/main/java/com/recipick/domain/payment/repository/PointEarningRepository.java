package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.PointEarning;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointEarningRepository extends JpaRepository<PointEarning, Long> {
}
