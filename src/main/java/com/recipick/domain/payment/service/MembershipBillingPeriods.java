package com.recipick.domain.payment.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

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

    // 갱신 회차의 기간. 입력은 접수 트랜잭션이 잠근 이용권 값(기준일·시작 시각·현재 종료 시각 T).
    // T가 원래 기준일로 센 k번째 달(k ≥ 1)의 기준 시각과 정확히 같아야 한다. 다음 종료 시각 E는 k + 1번째 달로 다시 센다 —
    // endAt.plusMonths(1)을 반복하지 않는다. 어긋나면 IllegalStateException(데이터 이상, 호출자가 접수하지 않는다)
    public static RenewalPeriod renewalPeriod(LocalDate billingAnchorDate, LocalDateTime startAt,
                                              LocalDateTime previousEndAt) {
        if (billingAnchorDate == null || startAt == null || previousEndAt == null) {
            throw new IllegalStateException("갱신 기간을 계산할 이용권 값이 비어 있습니다");
        }
        if (!billingAnchorDate.equals(startAt.toLocalDate()) || !startAt.isBefore(previousEndAt)) {
            throw new IllegalStateException("이용권 기준일·시작 시각·종료 시각의 관계가 올바르지 않습니다");
        }
        int months = (int) ChronoUnit.MONTHS.between(YearMonth.from(billingAnchorDate),
            YearMonth.from(previousEndAt.toLocalDate()));
        if (months < 1) {
            throw new IllegalStateException("갱신할 달을 계산할 수 없습니다");
        }
        LocalDateTime expected = anchorDate(billingAnchorDate, months).atTime(startAt.toLocalTime());
        if (!expected.equals(previousEndAt)) {
            throw new IllegalStateException("이용권 종료 시각이 원래 기준일과 맞지 않습니다");
        }
        LocalDateTime nextEndAt = anchorDate(billingAnchorDate, months + 1).atTime(startAt.toLocalTime());
        return RenewalPeriod.of(previousEndAt, nextEndAt);
    }

    // 저장된 갱신 회차에서 T·E를 되살린다. T = graceDeadlineAt - 3일, E = periodEndDate + 1일 + T의 시·분·초.
    // 유예가 정확히 3일이라는 정책에 기대는 식이다 — 유예 정책을 바꾸면 이 복원식도 함께 바꿔야 한다(설정만 바꾸면 안 된다)
    public static RenewalPeriod renewalPeriodOf(LocalDateTime graceDeadlineAt, LocalDate periodStartDate,
                                                LocalDate periodEndDate) {
        if (graceDeadlineAt == null || periodStartDate == null || periodEndDate == null) {
            throw new IllegalStateException("저장된 갱신 회차의 기간 값이 비어 있습니다");
        }
        LocalDateTime previousEndAt = graceDeadlineAt.minus(GRACE);
        LocalDateTime nextEndAt = periodEndDate.plusDays(1).atTime(previousEndAt.toLocalTime());
        RenewalPeriod period = RenewalPeriod.of(previousEndAt, nextEndAt);
        if (!period.periodStart().equals(periodStartDate)) {
            throw new IllegalStateException("저장된 갱신 회차의 시작일이 유예 마감과 맞지 않습니다");
        }
        return period;
    }

    // 갱신 회차 기간. T = 직전 이용 종료, G = T + 3일(결제 마감, 회차에 저장·불변), E = 성공 시 새 종료, graceEndAt = E + 3일.
    // 관계가 어긋난 값으로는 만들 수 없다(compact constructor가 모두 검사한다)
    public record RenewalPeriod(LocalDate periodStart, LocalDate periodEnd, LocalDateTime previousEndAt,
                                LocalDateTime graceDeadlineAt, LocalDateTime endAt, LocalDateTime graceEndAt) {

        public RenewalPeriod {
            if (periodStart == null || periodEnd == null || previousEndAt == null || graceDeadlineAt == null
                || endAt == null || graceEndAt == null) {
                throw new IllegalStateException("갱신 기간 값이 비어 있습니다");
            }
            if (!previousEndAt.isBefore(endAt)
                || !graceDeadlineAt.equals(previousEndAt.plus(GRACE))
                || !periodStart.equals(previousEndAt.toLocalDate())
                || !periodEnd.equals(endAt.toLocalDate().minusDays(1))
                || !periodStart.isBefore(periodEnd)
                || !graceEndAt.equals(endAt.plus(GRACE))) {
                throw new IllegalStateException("갱신 기간 값의 관계가 올바르지 않습니다");
            }
        }

        static RenewalPeriod of(LocalDateTime previousEndAt, LocalDateTime endAt) {
            return new RenewalPeriod(previousEndAt.toLocalDate(), endAt.toLocalDate().minusDays(1), previousEndAt,
                previousEndAt.plus(GRACE), endAt, endAt.plus(GRACE));
        }
    }

    // 첫 결제 접수 유효기간의 끝. 기준은 항상 그 회차의 "첫 번째 시도" requestedAt — 재발급해도 창을 다시 시작하지 않는다
    public static LocalDateTime firstChargeAcceptUntil(LocalDateTime firstAttemptRequestedAt, Duration acceptWindow) {
        if (firstAttemptRequestedAt == null || acceptWindow == null || acceptWindow.isNegative()
            || acceptWindow.isZero()) {
            throw new IllegalArgumentException("접수 유효 창 값이 올바르지 않습니다");
        }
        return firstAttemptRequestedAt.plus(acceptWindow);
    }

    // 첫 결제 새 시도의 임대 만료 = min(now + lease, acceptUntil). 29분 59초에 만든 시도도 30분을 넘겨 실행되지 않는다
    public static LocalDateTime firstChargeLeaseUntil(LocalDateTime now, Duration lease, LocalDateTime acceptUntil) {
        return leaseUntil(now, lease, acceptUntil);
    }

    // 새 시도의 임대 만료 = min(now + lease, until). until은 첫 결제면 접수 유효 창의 끝, 갱신이면 결제 마감 G다
    public static LocalDateTime leaseUntil(LocalDateTime now, Duration lease, LocalDateTime until) {
        if (now == null || lease == null || lease.isNegative() || lease.isZero() || until == null) {
            throw new IllegalArgumentException("임대 만료 값이 올바르지 않습니다");
        }
        LocalDateTime byLease = now.plus(lease);
        return byLease.isBefore(until) ? byLease : until;
    }
}
