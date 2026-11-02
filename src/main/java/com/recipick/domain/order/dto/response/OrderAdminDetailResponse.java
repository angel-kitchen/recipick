package com.recipick.domain.order.dto.response;

import com.recipick.domain.order.dto.OrderNameFormatter;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.entity.OrderStatusHistory;
import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.entity.enums.OrderStatusActor;
import com.recipick.domain.order.entity.enums.OrderStatusType;
import java.time.LocalDateTime;
import java.util.List;

// 결제 정보는 회원 상세와 같은 이유로 넣지 않는다. cancelable은 회원 취소 버튼용이라 빼고,
// 수락·거절 가능 여부는 그 기능을 구현할 때 넣는다
public record OrderAdminDetailResponse(
        Long orderId,
        String orderNumber,
        LocalDateTime createdAt,
        OrderStatus status,
        MartStatus martStatus,
        Long memberId,
        Long martId,
        String orderName,
        List<OrderDetailResponse.Item> items,
        Long productAmount,
        Long deliveryFee,
        Long usedPoint,
        Long payAmount,
        String receiverName,
        String receiverPhone,
        String address,
        String detailAddress,
        CancelReasonCode cancelReasonCode,
        String cancelReasonDetail,
        List<StatusHistory> histories
) {

    public static OrderAdminDetailResponse of(Order order, List<OrderItem> items, List<OrderStatusHistory> histories) {
        return new OrderAdminDetailResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCreatedAt(),
                order.getStatus(),
                order.getMartStatus(),
                order.getMemberId(),
                order.getMartId(),
                OrderNameFormatter.format(items),
                items.stream().map(OrderDetailResponse.Item::from).toList(),
                order.getProductAmount(),
                order.getDeliveryFee(),
                order.getUsedPoint(),
                order.getPayAmount(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getAddress(),
                order.getDetailAddress(),
                order.getCancelReasonCode(),
                order.getCancelReasonDetail(),
                histories.stream().map(StatusHistory::from).toList()
        );
    }

    public record StatusHistory(
            OrderStatusType statusType,
            String fromStatus,
            String toStatus,
            OrderStatusActor actor,
            Long changedByAdminId,
            String reason,
            LocalDateTime createdAt
    ) {

        static StatusHistory from(OrderStatusHistory history) {
            return new StatusHistory(
                    history.getStatusType(),
                    history.getFromStatus(),
                    history.getToStatus(),
                    history.getActor(),
                    history.getChangedByAdminId(),
                    history.getReason(),
                    history.getCreatedAt()
            );
        }
    }
}
