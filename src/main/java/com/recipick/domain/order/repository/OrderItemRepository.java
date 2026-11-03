package com.recipick.domain.order.repository;

import com.recipick.domain.order.entity.OrderItem;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findAllByOrderId(Long orderId);

    List<OrderItem> findAllByOrderIdOrderByIdAsc(Long orderId);

    List<OrderItem> findAllByOrderIdInOrderByIdAsc(Collection<Long> orderIds);

    @Query(value = """
            SELECT oi.productId
            FROM OrderItem oi
            JOIN oi.order o
            WHERE o.martId = :martId
            AND o.status = :status
            AND o.martStatus <> :rejected
            AND o.paidAt >= :from
            GROUP BY oi.productId
            ORDER BY SUM(oi.quantity) DESC, oi.productId DESC
            """,
        countQuery = """
            SELECT COUNT(DISTINCT oi.productId)
            FROM OrderItem oi
            JOIN oi.order o
            WHERE o.martId = :martId
            AND o.status = :status
            AND o.martStatus <> :rejected
            AND o.paidAt >= :from
            """)
    Page<Long> findBestSellingProductIds(
        @Param("martId") Long martId,
        @Param("status") OrderStatus status,
        @Param("rejected") MartStatus rejected,
        @Param("from") LocalDateTime from,
        Pageable pageable
    );

}
