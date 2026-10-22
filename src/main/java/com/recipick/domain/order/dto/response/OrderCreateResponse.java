package com.recipick.domain.order.dto.response;

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
                orderName(items),
                order.getProductAmount(),
                order.getDeliveryFee(),
                payment.amount(),
                payment.paymentId(),
                payment.portonePaymentId(),
                payment.expiresAt(),
                order.getReceiverName(),
                order.getReceiverPhone()
        );
    }

    // PortOne 결제창에 보여 줄 주문명. 컬럼으로 저장하지 않고 주문 상품 스냅샷에서 만든다
    private static String orderName(List<OrderItem> items) {
        String firstName = items.get(0).getProductName();
        return items.size() == 1 ? firstName : firstName + " 외 " + (items.size() - 1) + "건";
    }
}
