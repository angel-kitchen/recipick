package com.recipick.domain.order.dto.response;

import com.recipick.domain.order.dto.OrderNameFormatter;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;

public record OrderAdminSummaryResponse(
        Long orderId,
        String orderNumber,
        LocalDateTime createdAt,
        OrderStatus status,
        MartStatus martStatus,
        String orderName,
        Long payAmount,
        String receiverName,
        Long martId
) {

    public static OrderAdminSummaryResponse of(Order order, List<OrderItem> items) {
        return new OrderAdminSummaryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCreatedAt(),
                order.getStatus(),
                order.getMartStatus(),
                OrderNameFormatter.format(items),
                order.getPayAmount(),
                order.getReceiverName(),
                order.getMartId()
        );
    }
}
