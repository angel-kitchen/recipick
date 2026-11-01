package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// 첫 결제 기간 계산. JVM 기본 시간대와 무관한 LocalDate·LocalDateTime 계산이라 U-1(UTC JVM)에서도 같다
class MembershipBillingPeriodsTest {

    @Test
    @DisplayName("11/06 10:03 결제면 기간 11/06~12/05, 종료 12/06 10:03, 유예 12/09 10:03이다")
    void firstPeriod() {
        MembershipBillingPeriods.FirstPeriod period =
            MembershipBillingPeriods.firstPeriod(LocalDateTime.of(2026, 11, 6, 10, 3));

        assertThat(period.periodStart()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(period.periodEnd()).isEqualTo(LocalDate.of(2026, 12, 5));
        assertThat(period.endAt()).isEqualTo(LocalDateTime.of(2026, 12, 6, 10, 3));
        assertThat(period.graceEndAt()).isEqualTo(LocalDateTime.of(2026, 12, 9, 10, 3));
    }

    @Test
    @DisplayName("23:50 결제도 그 날짜가 기준일이다(서버 확인 시각이 아니라 PG 시각)")
    void lateNightPayment() {
        MembershipBillingPeriods.FirstPeriod period =
            MembershipBillingPeriods.firstPeriod(LocalDateTime.of(2026, 11, 6, 23, 50));

        assertThat(period.periodStart()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(period.endAt()).isEqualTo(LocalDateTime.of(2026, 12, 6, 23, 50));
    }

    @ParameterizedTest
    @CsvSource({
        "2026-01-31, 1, 2026-02-28",
        "2026-01-31, 2, 2026-03-31",
        "2026-01-31, 3, 2026-04-30",
        "2028-01-31, 1, 2028-02-29",
        "2026-01-30, 1, 2026-02-28",
        "2026-11-06, 0, 2026-11-06",
        "2026-11-06, 12, 2027-11-06"
    })
    @DisplayName("기준일의 '일'은 원래 기준일에서 다시 계산한다 — 1/31 → 2/28 → 3/31 → 4/30, 윤년 2/29")
    void anchorDateRecalculatesFromOriginalAnchor(LocalDate anchor, int monthsAfter, LocalDate expected) {
        assertThat(MembershipBillingPeriods.anchorDate(anchor, monthsAfter)).isEqualTo(expected);
    }

    @Test
    @DisplayName("1/31 결제의 첫 기간 종료는 2/28 같은 시각이다")
    void endOfJanuaryPayment() {
        MembershipBillingPeriods.FirstPeriod period =
            MembershipBillingPeriods.firstPeriod(LocalDateTime.of(2026, 1, 31, 9, 0));

        assertThat(period.periodEnd()).isEqualTo(LocalDate.of(2026, 2, 27));
        assertThat(period.endAt()).isEqualTo(LocalDateTime.of(2026, 2, 28, 9, 0));
    }

    @Test
    @DisplayName("결제 시각이 없거나 개월 수가 음수면 예외다")
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> MembershipBillingPeriods.firstPeriod(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MembershipBillingPeriods.anchorDate(LocalDate.of(2026, 1, 31), -1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- 첫 결제 접수 유효 창(확정점 ①, v4 최종 ③) ----------

    @Test
    @DisplayName("acceptUntil = 첫 시도 requestedAt + 30분. 경계(정확히 30분)는 닫힌 창이다(now.isBefore 기준)")
    void firstChargeAcceptUntil() {
        LocalDateTime first = LocalDateTime.of(2026, 11, 6, 10, 0);

        LocalDateTime until = MembershipBillingPeriods.firstChargeAcceptUntil(first, Duration.ofMinutes(30));

        assertThat(until).isEqualTo(first.plusMinutes(30));
        assertThat(first.plusMinutes(29).plusSeconds(59).isBefore(until)).isTrue();
        assertThat(until.isBefore(until)).isFalse();
    }

    @Test
    @DisplayName("새 시도의 임대 만료 = min(now + L, acceptUntil): 창 안쪽은 now + L, 창 끝에 가까우면 창의 끝")
    void firstChargeLeaseUntilNeverPassesAcceptUntil() {
        LocalDateTime first = LocalDateTime.of(2026, 11, 6, 10, 0);
        LocalDateTime until = first.plusMinutes(30);
        Duration lease = Duration.ofMinutes(10);

        assertThat(MembershipBillingPeriods.firstChargeLeaseUntil(first, lease, until))
            .isEqualTo(first.plusMinutes(10));
        assertThat(MembershipBillingPeriods.firstChargeLeaseUntil(first.plusMinutes(20), lease, until))
            .isEqualTo(until);
        assertThat(MembershipBillingPeriods.firstChargeLeaseUntil(first.plusMinutes(29).plusSeconds(59), lease, until))
            .isEqualTo(until);
    }

    @Test
    @DisplayName("창·임대가 0 이하이거나 값이 없으면 받지 않는다")
    void rejectsInvalidWindow() {
        LocalDateTime first = LocalDateTime.of(2026, 11, 6, 10, 0);
        assertThatThrownBy(() -> MembershipBillingPeriods.firstChargeAcceptUntil(first, Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MembershipBillingPeriods.firstChargeAcceptUntil(null, Duration.ofMinutes(30)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MembershipBillingPeriods.firstChargeLeaseUntil(first, Duration.ofMinutes(-1), first))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MembershipBillingPeriods.firstChargeLeaseUntil(first, Duration.ofMinutes(10), null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
