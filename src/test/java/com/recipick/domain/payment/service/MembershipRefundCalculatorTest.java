package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.payment.service.MembershipRefundCalculator.Calculation;
import java.time.LocalDateTime;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 결제기간 [시작, 종료)의 일수(종료일 중복 없음)·이용일수(시작일·요청일 포함)·정수 계산. 검산값은 결제액 3,900원 기준
class MembershipRefundCalculatorTest {

    private static final long FEE = 3_900L;

    @Test
    @DisplayName("1/31 10:03 → 2/28 10:03은 28일, 첫날 요청은 이용 1일·요금 139·위약금 780·환불 2,981")
    void periodDaysFollowActualMonth() {
        // given
        LocalDateTime start = LocalDateTime.of(2027, 1, 31, 10, 3);
        LocalDateTime end = LocalDateTime.of(2027, 2, 28, 10, 3);

        // when
        Calculation result = MembershipRefundCalculator.calculate(FEE, start, end, start.plusHours(2));

        // then
        assertThat(result).isEqualTo(new Calculation(28, 1, 139L, 780L, 2_981L));
    }

    @Test
    @DisplayName("29·30·31일 기간의 첫날 요청은 이용요금 134·130·125, 환불 2,986·2,990·2,995")
    void firstDayAcrossMonthLengths() {
        // given
        LocalDateTime start29 = LocalDateTime.of(2028, 1, 31, 10, 3);
        LocalDateTime start30 = LocalDateTime.of(2027, 3, 31, 10, 3);
        LocalDateTime start31 = LocalDateTime.of(2027, 2, 28, 10, 3);

        // when
        Calculation days29 = MembershipRefundCalculator.calculate(FEE, start29, LocalDateTime.of(2028, 2, 29, 10, 3),
            start29);
        Calculation days30 = MembershipRefundCalculator.calculate(FEE, start30, LocalDateTime.of(2027, 4, 30, 10, 3),
            start30);
        Calculation days31 = MembershipRefundCalculator.calculate(FEE, start31, LocalDateTime.of(2027, 3, 31, 10, 3),
            start31);

        // then
        assertThat(days29).isEqualTo(new Calculation(29, 1, 134L, 780L, 2_986L));
        assertThat(days30).isEqualTo(new Calculation(30, 1, 130L, 780L, 2_990L));
        assertThat(days31).isEqualTo(new Calculation(31, 1, 125L, 780L, 2_995L));
    }

    @Test
    @DisplayName("윤년 2028-01-31 → 2028-02-29는 29일이다")
    void leapFebruary() {
        // given
        LocalDateTime start = LocalDateTime.of(2028, 1, 31, 0, 0);
        LocalDateTime end = LocalDateTime.of(2028, 2, 29, 0, 0);

        // when
        Calculation result = MembershipRefundCalculator.calculate(FEE, start, end, start);

        // then
        assertThat(result.periodDays()).isEqualTo(29);
    }

    @Test
    @DisplayName("31일 기간의 10일째 요청은 이용요금 1,258·환불 1,862(1,902가 아니다)")
    void tenOfThirtyOneDays() {
        // given
        LocalDateTime start = LocalDateTime.of(2027, 2, 28, 10, 3);
        LocalDateTime end = LocalDateTime.of(2027, 3, 31, 10, 3);

        // when
        Calculation result = MembershipRefundCalculator.calculate(FEE, start, end, start.plusDays(9).plusHours(5));

        // then
        assertThat(result).isEqualTo(new Calculation(31, 10, 1_258L, 780L, 1_862L));
    }

    @Test
    @DisplayName("요청일이 시작일과 같은 날이면 시각이 달라도 이용 1일이고, 다음 날 0시면 이용 2일이다")
    void sameDayRequestCountsOneDay() {
        // given
        LocalDateTime start = LocalDateTime.of(2027, 3, 31, 10, 3);
        LocalDateTime end = LocalDateTime.of(2027, 4, 30, 10, 3);

        // when
        Calculation sameDay = MembershipRefundCalculator.calculate(FEE, start, end, start.withHour(23).withMinute(59));
        Calculation nextMidnight = MembershipRefundCalculator.calculate(FEE, start, end,
            start.toLocalDate().plusDays(1).atStartOfDay());

        // then
        assertThat(sameDay.usedDays()).isEqualTo(1);
        assertThat(nextMidnight.usedDays()).isEqualTo(2);
    }

    @Test
    @DisplayName("30일 중 24일 요청은 환불 0원이고 음수가 되지 않으며, 유예기간 요청의 이용일수는 결제기간 일수로 제한된다")
    void refundNeverNegative() {
        // given
        LocalDateTime start = LocalDateTime.of(2027, 3, 31, 10, 3);
        LocalDateTime end = LocalDateTime.of(2027, 4, 30, 10, 3);

        // when
        Calculation day24 = MembershipRefundCalculator.calculate(FEE, start, end, start.plusDays(23));
        Calculation grace = MembershipRefundCalculator.calculate(FEE, start, end, end.plusDays(2));

        // then
        assertThat(day24).isEqualTo(new Calculation(30, 24, 3_120L, 780L, 0L));
        assertThat(grace.usedDays()).isEqualTo(30);
        assertThat(grace.refundAmount()).isZero();
    }

    @Test
    @DisplayName("JVM 기본 시간대가 UTC여도 KST LocalDateTime 입력의 결과는 같다")
    void sameResultUnderUtcDefaultZone() {
        // given
        LocalDateTime start = LocalDateTime.of(2027, 2, 28, 10, 3);
        LocalDateTime end = LocalDateTime.of(2027, 3, 31, 10, 3);
        LocalDateTime requested = start.plusDays(9).plusHours(5);
        Calculation kst = MembershipRefundCalculator.calculate(FEE, start, end, requested);
        TimeZone original = TimeZone.getDefault();

        // when
        Calculation utc;
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            utc = MembershipRefundCalculator.calculate(FEE, start, end, requested);
        } finally {
            TimeZone.setDefault(original);
        }

        // then
        assertThat(utc).isEqualTo(kst);
    }

    @Test
    @DisplayName("결제액 0 이하·기간 역순·시작 전 요청·null은 계산하지 않는다")
    void rejectsInvalidInput() {
        // given
        LocalDateTime start = LocalDateTime.of(2027, 2, 28, 10, 3);
        LocalDateTime end = LocalDateTime.of(2027, 3, 31, 10, 3);

        // when & then
        assertThatThrownBy(() -> MembershipRefundCalculator.calculate(0L, start, end, start))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MembershipRefundCalculator.calculate(FEE, end, start, start))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MembershipRefundCalculator.calculate(FEE, start, end, start.minusSeconds(1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MembershipRefundCalculator.calculate(FEE, start, end, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MembershipRefundCalculator.calculate(FEE, start, start.plusHours(1), start))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
