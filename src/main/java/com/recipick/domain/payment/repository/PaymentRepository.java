package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
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

    // 결제 만료 Fallback용. 만료 재조회 구간이 지나도록 IN_PROGRESS인 결제 ID만 읽는다. 상태를 바꾸지 않고 큐에만 넣으므로
    // 엔티티를 읽지 않는다. id 기준으로 이어 읽어 처리 중 상태가 바뀌어도 offset처럼 행을 건너뛰지 않는다.
    // 만료 시각이 없는 결제는 기준이 없어 자동으로 다시 넣지 않는다
    @Query("""
        SELECT p.id FROM Payment p
         WHERE p.id > :afterId
           AND p.status = com.recipick.domain.payment.entity.enums.PaymentStatus.IN_PROGRESS
           AND p.expiresAt IS NOT NULL
           AND p.expiresAt <= :expiredBefore
         ORDER BY p.id
        """)
    List<Long> findStaleInProgressPaymentIds(@Param("afterId") Long afterId,
                                             @Param("expiredBefore") LocalDateTime expiredBefore,
                                             Pageable pageable);
}
