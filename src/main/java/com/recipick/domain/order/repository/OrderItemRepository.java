package com.recipick.domain.order.repository;

import com.recipick.domain.order.entity.OrderItem;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import com.recipick.domain.order.entity.enums.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findAllByOrderId(Long orderId);

    List<OrderItem> findAllByOrderIdOrderByIdAsc(Long orderId);

    List<OrderItem> findAllByOrderIdInOrderByIdAsc(Collection<Long> orderIds);

    // 마트가 거절한 주문은 status가 CANCEL_REQUESTED로 함께 바뀌므로 status = PAID 조건만으로 빠진다.
    // 페이지 계산은 삭제 상품을 거른 뒤 상품 도메인이 하므로, 여기서는 상위 N개 ID만 순서대로 돌려준다
    @Query("""
            SELECT oi.productId
            FROM OrderItem oi
            JOIN oi.order o
            WHERE o.martId = :martId
            AND o.status = :status
            AND o.paidAt >= :from
            GROUP BY oi.productId
            ORDER BY SUM(oi.quantity) DESC, oi.productId DESC
            """)
    List<Long> findBestSellingProductIds(
        @Param("martId") Long martId,
        @Param("status") OrderStatus status,
        @Param("from") LocalDateTime from,
        Pageable pageable
    );

}
