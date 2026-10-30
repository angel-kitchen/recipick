package com.recipick.domain.order.dto.response;

import com.recipick.domain.order.dto.OrderNameFormatter;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.payment.dto.PaymentCreated;
import java.time.LocalDateTime;
import java.util.List;

public record OrderCreateResponse(
    Long orderId,
    String orderNumber,
    OrderStatus status,
    String orderName,
    Long productAmount,
    Long deliveryFee,
    Long usedPoint,
    Long amount,
    Long paymentId,
    String portonePaymentId,
    LocalDateTime expiresAt,
    String receiverName,
    String receiverPhone
) {

    public static OrderCreateResponse of(Order order, List<OrderItem> items, PaymentCreated payment) {
        return new OrderCreateResponse(
            order.getId(),
            order.getOrderNumber(),
            order.getStatus(),
            OrderNameFormatter.format(items),
            order.getProductAmount(),
            order.getDeliveryFee(),
            order.getUsedPoint(),
            payment.amount(),
            payment.paymentId(),
            payment.portonePaymentId(),
            payment.expiresAt(),
            order.getReceiverName(),
            order.getReceiverPhone()
        );
    }
}
