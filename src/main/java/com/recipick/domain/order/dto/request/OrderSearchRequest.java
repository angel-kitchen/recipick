package com.recipick.domain.order.dto.request;

import com.recipick.domain.order.entity.enums.OrderStatusGroup;

// statusGroup을 생략하면 전체 탭이다
public record OrderSearchRequest(
        OrderStatusGroup statusGroup
) {
}
