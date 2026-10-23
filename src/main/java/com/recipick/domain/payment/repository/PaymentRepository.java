package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    // 정렬을 메서드 이름에 박지 않는다. 호출자가 넘긴 Pageable의 Sort만 적용돼 ORDER BY가 하나로 유지된다.
    Page<Payment> findByOrderIdIn(Collection<Long> orderIds, Pageable pageable);
}
