package com.recipick.domain.order.service;

import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.global.exception.BusinessException;

public record OrderAmount(long productAmount, long deliveryFee, long payAmount) {

    public static final long FREE_DELIVERY_THRESHOLD = 40_000L;
    public static final long MEMBERSHIP_FREE_DELIVERY_THRESHOLD = 15_000L;
    public static final long DELIVERY_FEE = 3_000L;

    // 체험 회원도 유료 회원 혜택 대상이라 멤버십 유형은 보지 않고 활성 여부만 받는다
    public static long freeDeliveryThreshold(boolean membershipActive) {
        return membershipActive ? MEMBERSHIP_FREE_DELIVERY_THRESHOLD : FREE_DELIVERY_THRESHOLD;
    }

    // 포인트를 쓰지 않는 금액. 주문 미리보기처럼 사용 포인트가 없는 계산이 쓴다
    public static OrderAmount of(long productAmount, boolean membershipActive) {
        return of(productAmount, 0L, membershipActive);
    }

    // 사용 포인트가 상품금액을 넘는지는 여기 한 곳에서만 본다. 주문 생성에서는 재고를 잠근 뒤의 가격으로 부르므로
    // 앞서 예약한 포인트·차감한 재고·저장한 주문은 이 예외로 함께 롤백된다.
    // 배송비는 포인트를 쓰기 전 상품금액으로 정하고, 포인트는 배송비에 쓸 수 없어 배송비를 그대로 더한다
    public static OrderAmount of(long productAmount, long usedPoint, boolean membershipActive) {
        if (usedPoint < 0) {
            throw new IllegalArgumentException("사용 포인트는 음수일 수 없습니다: " + usedPoint);
        }
        if (usedPoint > productAmount) {
            throw new BusinessException(OrderErrorCode.USED_POINT_EXCEEDS_PRODUCT_AMOUNT);
        }
        long deliveryFee = productAmount >= freeDeliveryThreshold(membershipActive) ? 0L : DELIVERY_FEE;
        return new OrderAmount(productAmount, deliveryFee, productAmount + deliveryFee - usedPoint);
    }
}
