package com.recipick.domain.order.service;

public record OrderAmount(long productAmount, long deliveryFee, long payAmount) {

    private static final long FREE_DELIVERY_THRESHOLD = 40_000L;
    private static final long DELIVERY_FEE = 3_000L;

    public static OrderAmount of(long productAmount) {
        long deliveryFee = productAmount >= FREE_DELIVERY_THRESHOLD ? 0L : DELIVERY_FEE;
        return new OrderAmount(productAmount, deliveryFee, productAmount + deliveryFee);
    }
}
