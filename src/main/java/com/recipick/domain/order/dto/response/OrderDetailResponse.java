package com.recipick.domain.order.dto.response;

import com.recipick.domain.order.dto.OrderNameFormatter;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.global.common.SaleUnit;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// 결제 정보(PortOne 결제 ID·만료 시각·결제 가능 여부)는 넣지 않는다. 결제 조회 API(GET /api/payments/orders/{orderId})가
// 그 값의 출처라 같은 값을 두 곳에 두지 않는다. orderName은 이어서 결제할 때 PortOne 결제창에 필요해 여기서 준다
public record OrderDetailResponse(
        Long orderId,
        String orderNumber,
        LocalDateTime createdAt,
        OrderStatus status,
        MartStatus martStatus,
        String orderName,
        boolean cancelable,
        List<Item> items,
        Long productAmount,
        Long deliveryFee,
        Long payAmount,
        String receiverName,
        String receiverPhone,
        String address,
        String detailAddress,
        CancelReasonCode cancelReasonCode,
        String cancelReasonDetail
) {

    public static OrderDetailResponse of(Order order, List<OrderItem> items) {
        return new OrderDetailResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCreatedAt(),
                order.getStatus(),
                order.getMartStatus(),
                OrderNameFormatter.format(items),
                // 결제 전 취소와 결제 후 취소가 같은 취소 API를 쓰므로 둘 중 하나면 취소 버튼을 보여 준다
                order.getStatus() == OrderStatus.PENDING_PAYMENT || order.isCancelableByMember(),
                items.stream().map(Item::from).toList(),
                order.getProductAmount(),
                order.getDeliveryFee(),
                order.getPayAmount(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getAddress(),
                order.getDetailAddress(),
                order.getCancelReasonCode(),
                order.getCancelReasonDetail()
        );
    }

    public record Item(
            Long productId,
            String productName,
            Long unitPrice,
            BigDecimal saleUnitAmount,
            SaleUnit saleUnit,
            int quantity,
            Long lineAmount
    ) {

        static Item from(OrderItem item) {
            return new Item(
                    item.getProductId(),
                    item.getProductName(),
                    item.getUnitPrice(),
                    item.getSaleUnitAmount(),
                    item.getSaleUnit(),
                    item.getQuantity(),
                    item.getLineAmount()
            );
        }
    }
}
