package com.recipick.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RankingPeriodTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 9, 0);

    @Test
    @DisplayName("일간·주간·월간은 기준 시각에서 24시간·7일·30일 전부터 시작한다")
    void startFrom_rolling() {
        // when, then
        assertThat(RankingPeriod.DAILY.startFrom(NOW)).isEqualTo(LocalDateTime.of(2026, 11, 2, 9, 0));
        assertThat(RankingPeriod.WEEKLY.startFrom(NOW)).isEqualTo(LocalDateTime.of(2026, 10, 27, 9, 0));
        assertThat(RankingPeriod.MONTHLY.startFrom(NOW)).isEqualTo(LocalDateTime.of(2026, 10, 4, 9, 0));
    }

    @Test
    @DisplayName("전체 기간은 모든 데이터보다 이른 고정 시각에서 시작한다")
    void startFrom_all() {
        // when
        LocalDateTime from = RankingPeriod.ALL.startFrom(NOW);

        // then
        assertThat(from).isEqualTo(LocalDateTime.of(2000, 1, 1, 0, 0));
        assertThat(from).isBefore(RankingPeriod.MONTHLY.startFrom(NOW));
    }
}
