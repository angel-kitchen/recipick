package com.recipick.domain.order.service;

import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.entity.OrderStatusHistory;
import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.entity.enums.OrderStatusActor;
import com.recipick.domain.order.entity.enums.OrderStatusType;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.repository.OrderItemRepository;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
import com.recipick.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    // 회원 취소·마트 거절은 결제 후 환불 흐름을 타므로 이 메서드로 종료하면 안 된다
    private static final Set<CancelReasonCode> PAYMENT_FAILURE_REASONS = EnumSet.of(
            CancelReasonCode.PAYMENT_FAILED, CancelReasonCode.PAYMENT_TIMEOUT, CancelReasonCode.AMOUNT_MISMATCH);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    public Long getMemberIdOf(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND))
                .getMemberId();
    }

    // 결제 Facade가 PAYMENT를 먼저 잠근 트랜잭션 안에서 호출한다(락 순서 PAYMENT → ORDERS)
    @Transactional
    public void markPaid(Long orderId, LocalDateTime paidAt) {
        Order order = getOrderForUpdate(orderId);
        // 결제 확정 API·웹훅·만료 큐가 같은 성공 결과를 반복 전달할 수 있다
        if (order.getStatus() == OrderStatus.PAID) {
            return;
        }

        OrderStatus fromStatus = order.getStatus();
        MartStatus fromMartStatus = order.getMartStatus();
        order.markPaid(paidAt);

        orderStatusHistoryRepository.saveAll(List.of(
                systemHistory(order, OrderStatusType.ORDER, fromStatus.name(), order.getStatus().name(), null),
                systemHistory(order, OrderStatusType.MART, fromMartStatus.name(), order.getMartStatus().name(), null)
        ));
    }

    // 재고 복구는 호출한 Facade가 하므로, 이번 호출로 종료된 경우에만 주문 상품을 돌려준다
    @Transactional
    public List<OrderItem> closeByPaymentFailure(
            Long orderId, CancelReasonCode reasonCode, String reasonDetail) {
        if (!PAYMENT_FAILURE_REASONS.contains(reasonCode)) {
            throw new IllegalArgumentException("결제 실패 사유가 아닙니다: " + reasonCode);
        }
        Order order = getOrderForUpdate(orderId);
        // 실패·만료 결과가 반복 전달돼도 최초 취소 사유를 덮어쓰거나 재고를 다시 복구하지 않는다
        if (order.getStatus() == OrderStatus.CANCELED) {
            return List.of();
        }

        OrderStatus fromStatus = order.getStatus();
        order.cancel(reasonCode, reasonDetail);

        orderStatusHistoryRepository.save(systemHistory(
                order, OrderStatusType.ORDER, fromStatus.name(), order.getStatus().name(), reasonCode.name()));
        return orderItemRepository.findAllByOrderId(orderId);
    }

    private Order getOrderForUpdate(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private OrderStatusHistory systemHistory(
            Order order, OrderStatusType statusType, String from, String to, String reason) {
        return OrderStatusHistory.builder()
                .order(order)
                .statusType(statusType)
                .fromStatus(from)
                .toStatus(to)
                .actor(OrderStatusActor.SYSTEM)
                .reason(reason)
                .build();
    }
}
