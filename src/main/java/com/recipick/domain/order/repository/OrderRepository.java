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

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Query("select o.id from Order o where o.memberId = :memberId order by o.id desc")
    List<Long> findIdsByMemberId(@Param("memberId") Long memberId);

    @Query("select o.id as orderId, o.memberId as memberId from Order o where o.id in :ids")
    List<OrderMemberId> findMemberIdsByIdIn(@Param("ids") Collection<Long> ids);
}
