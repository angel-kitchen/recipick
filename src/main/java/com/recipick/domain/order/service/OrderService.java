package com.recipick.domain.order.service;

import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderStatusHistory;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.entity.enums.OrderStatusActor;
import com.recipick.domain.order.entity.enums.OrderStatusType;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
import com.recipick.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    public Long getMemberIdOf(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND))
                .getMemberId();
    }

    // 결제 Facade가 PAYMENT를 먼저 잠근 트랜잭션 안에서 호출한다(락 순서 PAYMENT → ORDERS)
    @Transactional
    public void markPaid(Long orderId, LocalDateTime paidAt) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        // 결제 확정 API·웹훅·만료 큐가 같은 성공 결과를 반복 전달할 수 있다
        if (order.getStatus() == OrderStatus.PAID) {
            return;
        }

        OrderStatus fromStatus = order.getStatus();
        MartStatus fromMartStatus = order.getMartStatus();
        order.markPaid(paidAt);

        orderStatusHistoryRepository.saveAll(List.of(
                systemHistory(order, OrderStatusType.ORDER, fromStatus.name(), order.getStatus().name()),
                systemHistory(order, OrderStatusType.MART, fromMartStatus.name(), order.getMartStatus().name())
        ));
    }

    private OrderStatusHistory systemHistory(Order order, OrderStatusType statusType, String from, String to) {
        return OrderStatusHistory.builder()
                .order(order)
                .statusType(statusType)
                .fromStatus(from)
                .toStatus(to)
                .actor(OrderStatusActor.SYSTEM)
                .build();
    }
}
