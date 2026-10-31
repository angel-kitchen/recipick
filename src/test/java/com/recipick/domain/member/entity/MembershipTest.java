package com.recipick.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipStatus;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MembershipTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 1, 10, 0);
    private static final LocalDateTime END = START.plusMonths(1);

    private Membership membership(MembershipType type, MembershipSource source) {
        return Membership.builder().type(type).source(source)
                .startAt(START).activatedAt(START).endAt(END).autoRenew(source != MembershipSource.ADMIN_GRANT).build();
    }

    @Test
    @DisplayName("아직 활성화되지 않은 결제 대기는 환불 완료 반영으로 종료하지 않는다")
    void rejectsUnactivatedRefundTermination() {
        // given
        Membership pending = Membership.builder().type(MembershipType.PAID).source(MembershipSource.SUBSCRIBE)
                .startAt(START).endAt(END).autoRenew(true).build();
        // when
        Throwable thrown = catchThrowable(() -> pending.terminateAfterRefund(START.plusDays(1)));
        // then
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.NOT_TERMINABLE));
        assertThat(pending.getTerminatedAt()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"TRIAL,TRIAL", "PAID,ADMIN_GRANT"})
    @DisplayName("체험과 관리자 지급은 시작 시각과 같은 활성화 시각을 기록한다")
    void recordsActivationAtStart(MembershipType type, MembershipSource source) {
        // given
        Membership membership = membership(type, source);
        // when
        LocalDateTime activatedAt = membership.getActivatedAt();
        // then
        assertThat(activatedAt).isEqualTo(START);
        assertThat(membership.getGraceEndAt()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"TRIAL,TRIAL", "PAID,ADMIN_GRANT"})
    @DisplayName("체험과 관리자 지급은 활성화 시각 누락이나 시작 시각과의 불일치를 거절한다")
    void rejectsInvalidActivation(MembershipType type, MembershipSource source) {
        // given
        var builder = Membership.builder().type(type).source(source).startAt(START).endAt(END);
        // when
        Throwable missing = catchThrowable(builder::build);
        Throwable different = catchThrowable(() -> builder.activatedAt(START.plusSeconds(1)).build());
        // then
        for (Throwable thrown : new Throwable[]{missing, different}) {
            assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        }
    }

    @ParameterizedTest
    @CsvSource({"-1,false", "0,true", "1,true"})
    @DisplayName("이용 시작 시각부터 활성이고 시작 전에는 비활성이다")
    void activeStartBoundary(long seconds, boolean expected) {
        // given
        Membership membership = membership(MembershipType.PAID, MembershipSource.SUBSCRIBE);
        // when
        boolean active = membership.isActive(START.plusSeconds(seconds));
        // then
        assertThat(active).isEqualTo(expected);
    }

    @Test
    @DisplayName("종료 시각 직전까지 활성이고 종료 시각부터 만료이다")
    void activeEndBoundary() {
        // given
        Membership membership = membership(MembershipType.PAID, MembershipSource.SUBSCRIBE);
        // when
        boolean activeBeforeEnd = membership.isActive(END.minusNanos(1));
        var statusBeforeEnd = membership.getStatus(END.minusNanos(1));
        boolean activeAtEnd = membership.isActive(END);
        var statusAtEnd = membership.getStatus(END);
        // then
        assertThat(activeBeforeEnd).isTrue();
        assertThat(statusBeforeEnd).contains(MembershipStatus.ACTIVE);
        assertThat(activeAtEnd).isFalse();
        assertThat(statusAtEnd).contains(MembershipStatus.EXPIRED);
    }

    @Test
    @DisplayName("미시작 이용권은 활성 상태나 임의의 새 상태로 표시하지 않는다")
    void noStatusBeforeStart() {
        // given
        Membership membership = membership(MembershipType.PAID, MembershipSource.SUBSCRIBE);
        // when
        var status = membership.getStatus(START.minusSeconds(1));
        // then
        assertThat(status).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"PAID,SUBSCRIBE", "PAID,TRIAL_CONVERT", "TRIAL,TRIAL"})
    @DisplayName("유료와 체험은 해지 예약 후에도 남은 기간 동안 활성이다")
    void reserveCancellation(MembershipType type, MembershipSource source) {
        // given
        Membership membership = membership(type, source);
        LocalDateTime cancelledAt = START.plusDays(1);
        // when
        membership.reserveCancellation(cancelledAt);
        membership.reserveCancellation(cancelledAt.plusDays(1));
        // then
        assertThat(membership.isAutoRenew()).isFalse();
        assertThat(membership.getCancelReservedAt()).isEqualTo(cancelledAt);
        assertThat(membership.getEndAt()).isEqualTo(END);
        assertThat(membership.isActive(cancelledAt)).isTrue();
        assertThat(membership.getStatus(cancelledAt)).contains(MembershipStatus.CANCEL_RESERVED);
        assertThat(membership.getStatus(END)).contains(MembershipStatus.EXPIRED);
    }

    @Test
    @DisplayName("관리자 지급 이용권의 자동갱신 해지는 거부한다")
    void rejectsAdminGrantCancellation() {
        // given
        Membership membership = membership(MembershipType.PAID, MembershipSource.ADMIN_GRANT);
        // when
        Throwable thrown = catchThrowable(() -> membership.reserveCancellation(START.plusDays(1)));
        // then
        assertThat(thrown)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.NOT_CANCELABLE));
    }

    @Test
    @DisplayName("활성이 아닌 이용권에는 해지 예약을 하지 않는다")
    void rejectsExpiredCancellation() {
        // given
        Membership membership = membership(MembershipType.PAID, MembershipSource.SUBSCRIBE);
        // when
        Throwable thrown = catchThrowable(() -> membership.reserveCancellation(END));
        // then
        assertThat(thrown)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.ACTIVE_MEMBERSHIP_NOT_FOUND));
    }

    @Test
    @DisplayName("환불 완료 반영은 혜택을 종료하며 반복 반영은 최초 시각을 유지한다")
    void terminateAfterRefund() {
        // given
        Membership membership = membership(MembershipType.PAID, MembershipSource.SUBSCRIBE);
        LocalDateTime completedAt = START.plusDays(3);
        membership.reserveCancellation(START.plusDays(1));
        // when
        membership.terminateAfterRefund(completedAt);
        membership.terminateAfterRefund(completedAt.plusDays(1));
        // then
        assertThat(membership.getTerminatedAt()).isEqualTo(completedAt);
        assertThat(membership.isAutoRenew()).isFalse();
        assertThat(membership.isActive(completedAt)).isFalse();
        assertThat(membership.getStatus(completedAt)).contains(MembershipStatus.TERMINATED);
        assertThat(membership.getStatus(END)).contains(MembershipStatus.TERMINATED);
    }

    @ParameterizedTest
    @CsvSource({"TRIAL,TRIAL", "PAID,ADMIN_GRANT"})
    @DisplayName("체험과 관리자 지급 이용권은 환불 후 종료 대상에서 제외한다")
    void rejectsNonPaidRefund(MembershipType type, MembershipSource source) {
        // given
        Membership membership = membership(type, source);
        // when
        Throwable thrown = catchThrowable(() -> membership.terminateAfterRefund(START.plusDays(1)));
        // then
        assertThat(thrown)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.NOT_TERMINABLE));
        assertThat(membership.getTerminatedAt()).isNull();
    }

    @Test
    @DisplayName("환불 결과가 기간 만료 후 확정되어도 종료 기록을 반영한다")
    void delayedRefundCompletion() {
        // given
        Membership membership = membership(MembershipType.PAID, MembershipSource.SUBSCRIBE);
        // when
        membership.terminateAfterRefund(END.plusDays(1));
        // then
        assertThat(membership.getTerminatedAt()).isEqualTo(END.plusDays(1));
        assertThat(membership.getStatus(END.plusDays(1))).contains(MembershipStatus.TERMINATED);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("종료 시각이 시작 시각보다 늦지 않은 이용권은 생성하지 않는다")
    void rejectsInvalidPeriod(int days) {
        // given
        var builder = Membership.builder().type(MembershipType.PAID).source(MembershipSource.SUBSCRIBE)
                .startAt(START).activatedAt(START).endAt(START.plusDays(days));
        // when
        Throwable thrown = catchThrowable(builder::build);
        // then
        assertThat(thrown).isInstanceOf(BusinessException.class);
    }
}
