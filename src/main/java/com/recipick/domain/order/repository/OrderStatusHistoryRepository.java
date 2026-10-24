package com.recipick.domain.order.repository;

import com.recipick.domain.order.entity.OrderStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    List<OrderStatusHistory> findAllByOrderId(Long orderId);

    // 같은 트랜잭션에서 저장한 이력은 시각이 같을 수 있어 id 순으로 읽어야 순서가 바뀌지 않는다
    List<OrderStatusHistory> findAllByOrderIdOrderByIdAsc(Long orderId);
}
