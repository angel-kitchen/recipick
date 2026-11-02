package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BillingKeyDeletionPolicyTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 22, 0);

    @Test
    @DisplayName("다음 확인 시각은 같은 시각이면 기한이 됐고 1나노초라도 뒤면 아직이다")
    void dueIsInclusiveButFutureIsNotDue() {
        // when, then
        assertThat(BillingKeyDeletionPolicy.due(null, NOW)).isTrue();
        assertThat(BillingKeyDeletionPolicy.due(NOW, NOW)).isTrue();
        assertThat(BillingKeyDeletionPolicy.due(NOW.plusNanos(1), NOW)).isFalse();
    }

    @Test
    @DisplayName("임대가 끝나면 새 토큰이 아직 없어도 실행권을 잃는다")
    void expiredLeaseIsNotOwnedEvenWithoutNewToken() {
        // when, then
        assertThat(BillingKeyDeletionPolicy.liveLease(2, 2, NOW.plusSeconds(1), NOW)).isTrue();
        assertThat(BillingKeyDeletionPolicy.liveLease(2, 2, NOW, NOW)).isFalse();
        assertThat(BillingKeyDeletionPolicy.liveLease(2, 1, NOW.plusMinutes(1), NOW)).isFalse();
        assertThat(BillingKeyDeletionPolicy.liveLease(0, 0, NOW.plusMinutes(1), NOW)).isFalse();
    }

    @Test
    @DisplayName("자동 DELETE 허용 창은 접수 시각부터 24시간 미만이다")
    void automaticDeleteStopsAtTwentyFourHours() {
        // when, then
        assertThat(BillingKeyDeletionPolicy.deleteWindowOpen(NOW, NOW.plusHours(24).minusNanos(1))).isTrue();
        assertThat(BillingKeyDeletionPolicy.deleteWindowOpen(NOW, NOW.plusHours(24))).isFalse();
        assertThat(BillingKeyDeletionPolicy.deleteWindowOpen(NOW, NOW.minusNanos(1))).isFalse();
    }

    @Test
    @DisplayName("재확인 간격은 1·2·4·8분으로 늘다가 10분에서 멈추고 실행 횟수 0은 거절한다")
    void delayIsBoundedWithoutArithmeticOverflow() {
        // when, then
        assertThat(BillingKeyDeletionPolicy.retryDelay(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(BillingKeyDeletionPolicy.retryDelay(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(BillingKeyDeletionPolicy.retryDelay(3)).isEqualTo(Duration.ofMinutes(4));
        assertThat(BillingKeyDeletionPolicy.retryDelay(4)).isEqualTo(Duration.ofMinutes(8));
        assertThat(BillingKeyDeletionPolicy.retryDelay(Integer.MAX_VALUE)).isEqualTo(Duration.ofMinutes(10));
        assertThatThrownBy(() -> BillingKeyDeletionPolicy.retryDelay(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
