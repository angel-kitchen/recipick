package com.recipick.domain.payment.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

// 멤버십 이용기간 계산. Spring을 모르는 순수 계산이라 단위 테스트가 직접 만든다.
// 기준일의 '일'은 항상 원래 anchor에서 다시 계산한다 — 달마다 누적해서 clamp하면 1/31 → 2/28 → 3/28이 되는 버그가 생긴다(I-9)
public final class MembershipBillingPeriods {

    public static final Duration GRACE = Duration.ofDays(3);

    private MembershipBillingPeriods() {
    }

    // 기준일의 '일'을 monthsAfter번째 달에 맞추고, 그 달에 없으면 말일. monthsAfter = 0이면 anchor 그대로
    public static LocalDate anchorDate(LocalDate anchor, int monthsAfter) {
        if (anchor == null || monthsAfter < 0) {
            throw new IllegalArgumentException("기준일과 0 이상의 개월 수가 필요합니다");
        }
        YearMonth month = YearMonth.from(anchor).plusMonths(monthsAfter);
        return month.atDay(Math.min(anchor.getDayOfMonth(), month.lengthOfMonth()));
    }

    // 첫 결제 기간. 시작·기준일 = PG paidAt(서버 확인 시각 아님). 종료 시각은 다음 기준일의 같은 시각, 유예는 +3일.
    // graceEndAt은 항상 non-null로 돌려준다 — 해지 예약이 있을 때 null로 바꾸는 것은 회원 도메인(C-3a)의 몫이다
    public static FirstPeriod firstPeriod(LocalDateTime pgPaidAt) {
        if (pgPaidAt == null) {
            throw new IllegalArgumentException("PG 결제 시각이 필요합니다");
        }
        LocalDate anchor = pgPaidAt.toLocalDate();
        LocalDate nextAnchor = anchorDate(anchor, 1);
        LocalDateTime endAt = nextAnchor.atTime(pgPaidAt.toLocalTime());
        return new FirstPeriod(anchor, nextAnchor.minusDays(1), endAt, endAt.plus(GRACE));
    }

    public record FirstPeriod(LocalDate periodStart, LocalDate periodEnd, LocalDateTime endAt,
                              LocalDateTime graceEndAt) {
    }

    // 첫 결제 접수 유효기간의 끝. 기준은 항상 그 회차의 "첫 번째 시도" requestedAt — 재발급해도 창을 다시 시작하지 않는다
    public static LocalDateTime firstChargeAcceptUntil(LocalDateTime firstAttemptRequestedAt, Duration acceptWindow) {
        if (firstAttemptRequestedAt == null || acceptWindow == null || acceptWindow.isNegative()
            || acceptWindow.isZero()) {
            throw new IllegalArgumentException("접수 유효 창 값이 올바르지 않습니다");
        }
        return firstAttemptRequestedAt.plus(acceptWindow);
    }

    // 새 시도의 임대 만료 = min(now + lease, acceptUntil). 29분 59초에 만든 시도도 30분을 넘겨 실행되지 않는다.
    // 첫 결제 전용 — 갱신 회차의 마감(grace_deadline_at)과 섞지 않는다
    public static LocalDateTime firstChargeLeaseUntil(LocalDateTime now, Duration lease, LocalDateTime acceptUntil) {
        if (now == null || lease == null || lease.isNegative() || lease.isZero() || acceptUntil == null) {
            throw new IllegalArgumentException("임대 만료 값이 올바르지 않습니다");
        }
        LocalDateTime byLease = now.plus(lease);
        return byLease.isBefore(acceptUntil) ? byLease : acceptUntil;
    }
}
