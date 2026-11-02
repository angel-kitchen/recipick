package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.payment.service.MembershipBillingPeriods.RenewalPeriod;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 갱신 기간 계산. 기준일은 언제나 원래 billingAnchorDate에서 다시 센다. JVM 시간대와 무관한 계산이라 UTC JVM에서도 같다
class MembershipBillingPeriodsRenewalTest {

    private static final LocalDate ANCHOR = LocalDate.of(2026, 1, 31);
    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 31, 10, 3);

    @Test
    @DisplayName("1/31 기준이면 2/28 → 3/31 → 4/30으로 이어지고 기간이 겹치거나 비지 않는다")
    void keepsOriginalAnchorAcrossMonthEnds() {
        // given
        LocalDateTime februaryEnd = LocalDateTime.of(2026, 2, 28, 10, 3);

        // when
        RenewalPeriod second = MembershipBillingPeriods.renewalPeriod(ANCHOR, START, februaryEnd);
        RenewalPeriod third = MembershipBillingPeriods.renewalPeriod(ANCHOR, START, second.endAt());

        // then
        assertThat(second.previousEndAt()).isEqualTo(februaryEnd);
        assertThat(second.endAt()).isEqualTo(LocalDateTime.of(2026, 3, 31, 10, 3));
        assertThat(second.periodStart()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(second.periodEnd()).isEqualTo(LocalDate.of(2026, 3, 30));
        assertThat(second.graceDeadlineAt()).isEqualTo(LocalDateTime.of(2026, 3, 3, 10, 3));
        assertThat(second.graceEndAt()).isEqualTo(LocalDateTime.of(2026, 4, 3, 10, 3));
        assertThat(third.endAt()).isEqualTo(LocalDateTime.of(2026, 4, 30, 10, 3));
        assertThat(third.periodStart()).isEqualTo(second.periodEnd().plusDays(1));
    }

    @Test
    @DisplayName("윤년 1/31 기준이면 2/29를 거쳐 3/31로 돌아온다")
    void leapYear() {
        // given
        LocalDate anchor = LocalDate.of(2028, 1, 31);
        LocalDateTime start = LocalDateTime.of(2028, 1, 31, 23, 59, 59);

        // when
        RenewalPeriod period = MembershipBillingPeriods.renewalPeriod(anchor, start,
            LocalDateTime.of(2028, 2, 29, 23, 59, 59));

        // then
        assertThat(period.endAt()).isEqualTo(LocalDateTime.of(2028, 3, 31, 23, 59, 59));
        assertThat(period.periodEnd()).isEqualTo(LocalDate.of(2028, 3, 30));
    }

    @Test
    @DisplayName("저장된 회차 값(G·시작일·종료일)으로 T·E를 정확히 되살린다")
    void renewalPeriodOfRestoresExactly() {
        // given
        RenewalPeriod created = MembershipBillingPeriods.renewalPeriod(ANCHOR, START,
            LocalDateTime.of(2026, 2, 28, 10, 3));

        // when
        RenewalPeriod restored = MembershipBillingPeriods.renewalPeriodOf(created.graceDeadlineAt(),
            created.periodStart(), created.periodEnd());

        // then
        assertThat(restored).isEqualTo(created);
    }

    @Test
    @DisplayName("저장된 시작일이 G − 3일의 날짜와 다르면 되살리지 않는다")
    void renewalPeriodOfRejectsInconsistentStart() {
        // given
        RenewalPeriod created = MembershipBillingPeriods.renewalPeriod(ANCHOR, START,
            LocalDateTime.of(2026, 2, 28, 10, 3));

        // when / then
        assertThatThrownBy(() -> MembershipBillingPeriods.renewalPeriodOf(created.graceDeadlineAt(),
            created.periodStart().minusDays(1), created.periodEnd()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("종료 시각이 원래 기준일과 맞지 않거나(2/27) 시작 시각과 기준일이 다르거나 첫 달이면 계산하지 않는다")
    void rejectsAnchorMismatch() {
        // when / then
        assertThatThrownBy(() -> MembershipBillingPeriods.renewalPeriod(ANCHOR, START,
            LocalDateTime.of(2026, 2, 27, 10, 3))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MembershipBillingPeriods.renewalPeriod(ANCHOR, START.plusDays(1),
            LocalDateTime.of(2026, 2, 28, 10, 3))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MembershipBillingPeriods.renewalPeriod(ANCHOR, START,
            LocalDateTime.of(2026, 1, 31, 18, 0))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MembershipBillingPeriods.renewalPeriod(null, START,
            LocalDateTime.of(2026, 2, 28, 10, 3))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("관계가 어긋난 값으로는 RenewalPeriod를 직접 만들 수 없다")
    void recordRejectsBrokenRelation() {
        // given
        LocalDateTime previousEnd = LocalDateTime.of(2026, 2, 28, 10, 3);
        LocalDateTime end = LocalDateTime.of(2026, 3, 31, 10, 3);

        // when / then
        assertThatThrownBy(() -> new RenewalPeriod(previousEnd.toLocalDate(), end.toLocalDate().minusDays(1),
            previousEnd, previousEnd.plusDays(2), end, end.plusDays(3))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("새 시도의 임대는 min(now + L, 마감)이다 — 마감 1분 전에 만든 시도는 마감에 끝난다")
    void leaseUntilIsMinOfLeaseAndDeadline() {
        // given
        LocalDateTime deadline = LocalDateTime.of(2026, 3, 3, 10, 3);
        Duration lease = Duration.ofMinutes(10);

        // when / then
        assertThat(MembershipBillingPeriods.leaseUntil(deadline.minusHours(1), lease, deadline))
            .isEqualTo(deadline.minusMinutes(50));
        assertThat(MembershipBillingPeriods.leaseUntil(deadline.minusMinutes(1), lease, deadline))
            .isEqualTo(deadline);
        assertThatThrownBy(() -> MembershipBillingPeriods.leaseUntil(deadline, Duration.ZERO, deadline))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
