package com.recipick.domain.order.facade;

import com.recipick.domain.order.dto.response.OrderCreateResponse;

// 같은 Idempotency-Key 재시도는 새로 만든 것이 아니라 기존 주문을 돌려준 것이라 응답 상태를 나눈다
public record OrderCreateResult(OrderCreateResponse response, boolean created) {
}
