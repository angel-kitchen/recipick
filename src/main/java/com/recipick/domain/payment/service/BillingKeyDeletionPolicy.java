package com.recipick.domain.payment.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

public final class BillingKeyDeletionPolicy {
    public static final Duration LEASE = Duration.ofMinutes(5);
    public static final Duration DELETE_WINDOW = Duration.ofHours(24);
    public static final Duration REVIEW_INTERVAL = Duration.ofHours(1);

    private BillingKeyDeletionPolicy() {
    }

    public static boolean due(LocalDateTime nextCheckAt, LocalDateTime now) {
        Objects.requireNonNull(now, "now");
        return nextCheckAt == null || !now.isBefore(nextCheckAt);
    }

    public static boolean liveLease(long storedToken, long token, LocalDateTime leaseExpiresAt, LocalDateTime now) {
        return token > 0 && storedToken == token && leaseExpiresAt != null && now.isBefore(leaseExpiresAt);
    }

    public static boolean deleteWindowOpen(LocalDateTime requestedAt, LocalDateTime now) {
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(now, "now");
        return !now.isBefore(requestedAt) && now.isBefore(requestedAt.plus(DELETE_WINDOW));
    }

    public static Duration retryDelay(int claimCount) {
        if (claimCount < 1) {
            throw new IllegalArgumentException("실행 횟수는 1 이상이어야 합니다");
        }
        return Duration.ofMinutes(Math.min(10L, 1L << Math.min(claimCount - 1, 4)));
    }

    public static boolean requiresReview(BillingKeyDeletionCode code) {
        return switch (Objects.requireNonNull(code, "code")) {
            case AUTH_ERROR, REQUEST_REJECTED, IDENTITY_MISMATCH, STATE_CONFLICT,
                 UNRESOLVED_ATTEMPT, WINDOW_EXPIRED, CHANNEL_NOT_TEST, SCOPE_UNPROVEN -> true;
            default -> false;
        };
    }
}
