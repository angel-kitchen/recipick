package com.recipick.domain.order.event;

import java.util.List;

// 결제 성공으로 주문이 PAID가 된 뒤의 후처리(장바구니 정리)에 쓴다. 결제 확정 트랜잭션이 커밋돼야 리스너가 받는다
public record OrderPaidEvent(Long orderId, Long memberId, List<Long> productIds) {
}
