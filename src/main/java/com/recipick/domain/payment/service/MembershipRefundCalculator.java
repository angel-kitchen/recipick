package com.recipick.domain.payment.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// 멤버십 즉시 해지 환불액 계산(순수 함수). 결제기간은 [periodStartAt, periodEndAt) — 종료 시각은 다음 결제기간이 시작되는 배타적 시각이라
// 일수에 +1을 붙이지 않는다(1/31 10:03 → 2/28 10:03은 28일). 이용일수는 시작일과 요청일을 모두 포함하고 결제기간 일수를 넘지 않는다.
// 입력 시각은 호출자가 한국 시간으로 만든 LocalDateTime이며 여기서 시간대를 다시 해석하지 않는다. double·반올림 없이 정수 나눗셈(내림)과
// Math.*Exact만 쓴다. 위약금은 결제액의 20%, 환불액은 0원 아래로 내려가지 않는다
public final class MembershipRefundCalculator {

    private static final long PENALTY_PERCENT = 20L;

    private MembershipRefundCalculator() {
    }

    public record Calculation(int periodDays, int usedDays, long usedDayFee, long penaltyAmount, long refundAmount) {
    }

    public static Calculation calculate(long paidAmount, LocalDateTime periodStartAt, LocalDateTime periodEndAt,
                                        LocalDateTime requestedAt) {
        if (paidAmount <= 0
            || periodStartAt == null
            || periodEndAt == null
            || requestedAt == null
            || !periodStartAt.isBefore(periodEndAt)
            || requestedAt.isBefore(periodStartAt)) {
            throw new IllegalArgumentException("환불 계산 값이 올바르지 않습니다");
        }
        long days = ChronoUnit.DAYS.between(periodStartAt.toLocalDate(), periodEndAt.toLocalDate());
        if (days <= 0) {
            throw new IllegalArgumentException("결제기간 일수는 1일 이상이어야 합니다");
        }
        long requestedDays = Math.addExact(
            ChronoUnit.DAYS.between(periodStartAt.toLocalDate(), requestedAt.toLocalDate()), 1L);
        int periodDays = Math.toIntExact(days);
        int usedDays = Math.toIntExact(Math.min(days, requestedDays));
        long usedDayFee = Math.multiplyExact(paidAmount, (long) usedDays) / days;
        long penaltyAmount = Math.multiplyExact(paidAmount, PENALTY_PERCENT) / 100L;
        long refundAmount = Math.max(0L,
            Math.subtractExact(Math.subtractExact(paidAmount, usedDayFee), penaltyAmount));
        return new Calculation(periodDays, usedDays, usedDayFee, penaltyAmount, refundAmount);
    }
}
