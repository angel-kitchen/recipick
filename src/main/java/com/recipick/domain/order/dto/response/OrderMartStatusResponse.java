package com.recipick.domain.order.dto.response;

import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;

// 수락·배송 상태 변경처럼 마트 처리 상태만 바뀌는 요청의 응답이다
public record OrderMartStatusResponse(
        Long orderId,
        OrderStatus status,
        MartStatus martStatus
) {

    public static OrderMartStatusResponse from(Order order) {
        return new OrderMartStatusResponse(order.getId(), order.getStatus(), order.getMartStatus());
    }
}
