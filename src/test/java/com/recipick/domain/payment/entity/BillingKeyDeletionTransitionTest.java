package com.recipick.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.payment.entity.enums.BillingKeyDeletionReason;
import com.recipick.domain.payment.entity.enums.BillingKeyDeletionStatus;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.service.BillingKeyDeletionCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BillingKeyDeletionTransitionTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 22, 0);

    @Test
    @DisplayName("삭제 요청된 키는 되살리거나 폐기로 되돌릴 수 없고 삭제 확인은 한 번만 반영된다")
    void deletionRequestCannotBeReactivatedOrDowngraded() {
        // given
        BillingKey key = key();

        // when, then
        assertThat(key.requestDeletion(NOW)).isTrue();
        assertThat(key.requestDeletion(NOW.plusMinutes(1))).isFalse();
        assertThat(key.getDeleteRequestedAt()).isEqualTo(NOW);
        assertThatThrownBy(key::reactivate).isInstanceOf(IllegalStateException.class);
        assertThat(key.revoke()).isFalse();
        assertThat(key.confirmRemoteDeletion(NOW.plusMinutes(2))).isTrue();
        assertThat(key.confirmRemoteDeletion(NOW.plusMinutes(3))).isFalse();
        assertThat(key.getDeletedAt()).isEqualTo(NOW.plusMinutes(2));
        assertThat(key.toString()).doesNotContain("secret-key");
    }

    @Test
    @DisplayName("임대가 끝난 실행자는 다음 실행자가 나타나기 전에도 예약·발송 준비를 바꾸지 못한다")
    void expiredOwnerCannotScheduleEvenBeforeNextClaim() {
        // given
        BillingKeyDeletion task = task();

        // when, then
        assertThat(task.claim(NOW)).isTrue();
        long token = task.getClaimToken();
        assertThat(task.defer(token, NOW.plusMinutes(5), BillingKeyDeletionCode.HTTP_ERROR)).isFalse();
        assertThat(task.prepareDelete(token, NOW.plusMinutes(5))).isFalse();
        assertThat(task.getLeaseExpiresAt()).isEqualTo(NOW.plusMinutes(5));
    }

    @Test
    @DisplayName("다음 확인 시각 전에는 실행권을 주지 않고 옛 토큰의 예약은 거절한다")
    void dueTimeAndTokenFenceAreBothEnforced() {
        // given
        BillingKeyDeletion task = task();

        // when, then
        task.claim(NOW);
        long old = task.getClaimToken();
        task.defer(old, NOW, BillingKeyDeletionCode.HTTP_ERROR);
        assertThat(task.claim(NOW.plusSeconds(59))).isFalse();
        assertThat(task.claim(NOW.plusMinutes(1))).isTrue();
        assertThat(task.getClaimToken()).isEqualTo(old + 1);
        assertThat(task.defer(old, NOW.plusMinutes(1), BillingKeyDeletionCode.AUTH_ERROR)).isFalse();
        assertThat(task.getStatus()).isEqualTo(BillingKeyDeletionStatus.REQUESTED);
    }

    @Test
    @DisplayName("운영 확인 상태는 1시간 간격 조회만 허용하고 검증된 삭제 관측으로 완료할 수 있다")
    void reviewRemainsObservableAndConfirmedAbsenceCanComplete() {
        // given
        BillingKeyDeletion task = task();

        // when, then
        task.review(BillingKeyDeletionCode.AUTH_ERROR, NOW);
        assertThat(task.claim(NOW.plusMinutes(59))).isFalse();
        assertThat(task.claim(NOW.plusHours(1))).isTrue();
        assertThat(task.prepareDelete(task.getClaimToken(), NOW.plusHours(1))).isFalse();
        task.complete(NOW.plusHours(1), BillingKeyDeletionCode.DELETED);
        assertThat(task.getStatus()).isEqualTo(BillingKeyDeletionStatus.COMPLETED);
        assertThat(task.claim(NOW.plusDays(2))).isFalse();
        assertThat(task.getLeaseExpiresAt()).isNull();
    }

    @Test
    @DisplayName("DELETE 응답만으로는 완료할 수 없다 — 삭제 확인 근거가 필요하다")
    void successRequiresAbsenceEvidence() {
        // given
        BillingKeyDeletion task = task();

        // when, then
        assertThatThrownBy(() -> task.complete(NOW, BillingKeyDeletionCode.DELETE_ACCEPTED))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static BillingKeyDeletion task() {
        return BillingKeyDeletion.requested(key(), "store-test", BillingKeyDeletionReason.REPLACED_KEY, NOW);
    }

    private static BillingKey key() {
        BillingKey key = BillingKey.builder().memberId(1L).portoneBillingKey("secret-key")
            .status(BillingKeyStatus.REPLACED).issuedAt(NOW.minusDays(1)).build();
        ReflectionTestUtils.setField(key, "id", 2L);
        return key;
    }
}
