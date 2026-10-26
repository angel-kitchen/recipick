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

public interface PaymentRepository extends JpaRepository<Payment, Long>, PaymentRepositoryCustom {

    Optional<Payment> findByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    // 취소 API는 결제 ID로 들어온다. 잠금 조회라 이름으로 구분한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    // 정렬을 메서드 이름에 박지 않는다. 호출자가 넘긴 Pageable의 Sort만 적용돼 ORDER BY가 하나로 유지된다.
    Page<Payment> findByOrderIdIn(Collection<Long> orderIds, Pageable pageable);

    // 웹훅은 PortOne 결제 ID로 들어온다. portone_payment_id가 UNIQUE라 최대 한 건이다. 잠금은 찾은 id로 다시 건다
    Optional<Payment> findByPortonePaymentId(String portonePaymentId);
}
