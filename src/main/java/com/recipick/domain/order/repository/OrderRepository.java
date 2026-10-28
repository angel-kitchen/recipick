package com.recipick.domain.order.repository;

import com.recipick.domain.order.entity.Order;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long>, OrderRepositoryCustom {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    Optional<Order> findByMemberIdAndIdempotencyKey(Long memberId, String idempotencyKey);

    boolean existsByPendingMemberId(Long pendingMemberId);

    Optional<Order> findByPendingMemberId(Long pendingMemberId);

    boolean existsByOrderNumber(String orderNumber);

    @Query("select o.id from Order o where o.memberId = :memberId order by o.id desc")
    List<Long> findIdsByMemberId(@Param("memberId") Long memberId);

    @Query("select o.id as orderId, o.memberId as memberId from Order o where o.id in :ids")
    List<OrderMemberId> findMemberIdsByIdIn(@Param("ids") Collection<Long> ids);

    // 엔티티를 읽으면 영속성 컨텍스트에 남아, 같은 트랜잭션의 뒤이은 findByIdForUpdate가 잠근 뒤에도 이전 상태를 받는다
    @Query("select o.memberId from Order o where o.id = :id")
    Optional<Long> findMemberIdById(@Param("id") Long id);

    @Query("select o.martId from Order o where o.id = :id")
    Optional<Long> findMartIdById(@Param("id") Long id);
}
