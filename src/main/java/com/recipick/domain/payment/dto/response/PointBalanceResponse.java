package com.recipick.domain.payment.dto.response;

import java.time.LocalDate;

// 오늘 실제로 쓸 수 있는 포인트와 가장 가까운 만료.
public record PointBalanceResponse(
    Long memberId,
    long availableBalance,
    long nearestExpiringAmount,
    LocalDate nearestExpiresOn
) {
}
