package com.recipick.global.common;

import java.time.Duration;

public final class PolicyConstants {

    public static final Duration PAYMENT_PENDING_TIMEOUT = Duration.ofMinutes(5);
    public static final int MART_SERVICE_RADIUS_METERS = 2000;
    public static final long MEMBERSHIP_MONTHLY_FEE = 3900L;

    private PolicyConstants() {
    }
}
