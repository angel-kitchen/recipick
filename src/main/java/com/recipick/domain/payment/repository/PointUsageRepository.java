package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.PointUsage;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointUsageRepository extends JpaRepository<PointUsage, Long> {

    List<PointUsage> findAllByOrderIdOrderByIdAsc(Long orderId);

    // 최신 상태를 읽어야 이미 복원·확정된 사용을 다시 처리하지 않는다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM PointUsage u WHERE u.orderId = :orderId ORDER BY u.id ASC")
    List<PointUsage> findAllByOrderIdForUpdate(@Param("orderId") Long orderId);
}
