package com.recipick.domain.order.dto.response;

import com.recipick.domain.order.dto.OrderNameFormatter;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;

public record OrderSummaryResponse(
        Long orderId,
        String orderNumber,
        LocalDateTime createdAt,
        OrderStatus status,
        MartStatus martStatus,
        String orderName,
        Long payAmount
) {

    public static OrderSummaryResponse of(Order order, List<OrderItem> items) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCreatedAt(),
                order.getStatus(),
                order.getMartStatus(),
                OrderNameFormatter.format(items),
                order.getPayAmount()
        );
    }
}
