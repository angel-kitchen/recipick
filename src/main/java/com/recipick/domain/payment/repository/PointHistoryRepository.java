package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.PointHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {
}
