package com.recipick.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.member.dto.MembershipBillingResult;
import com.recipick.domain.member.dto.MembershipConsent;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.global.exception.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MembershipBillingTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 31, 12, 0);
    private static final LocalDateTime END = START.plusMonths(1);
    private static final MembershipConsent CONSENT = new MembershipConsent(3900L, "v1", START);

    private Membership pending() {
        return Membership.pendingPaid(Member.builder().build(), MembershipSource.SUBSCRIBE, START, END, CONSENT);
    }

    private Membership trial() {
        return Membership.builder().member(Member.builder().build()).type(MembershipType.TRIAL)
                .source(MembershipSource.TRIAL).startAt(START).activatedAt(START).endAt(START.plusDays(7))
                .autoRenew(true).build();
    }

    @Test
    @DisplayName("첫 결제는 PG 시각으로 실제 이용 기간과 기준일을 기록하며 반복 성공은 덮어쓰지 않는다")
    void activatesExactlyOnceAtPgTime() {
        // given
        Membership value = pending();
        LocalDateTime paidAt = START.plusHours(1);
        // when
        var first = value.activateAfterFirstPayment(paidAt, paidAt.plusMonths(1), paidAt.plusMonths(1).plusDays(3));
        var repeated = value.activateAfterFirstPayment(paidAt.plusDays(1), paidAt.plusMonths(2), null);
        // then
        assertThat(first).isEqualTo(MembershipBillingResult.ACTIVATED);
        assertThat(repeated).isEqualTo(MembershipBillingResult.ALREADY_APPLIED);
        assertThat(value.getStartAt()).isEqualTo(paidAt);
        assertThat(value.getActivatedAt()).isEqualTo(paidAt);
        assertThat(value.getEndAt()).isEqualTo(paidAt.plusMonths(1));
        assertThat(value.getBillingAnchorDate()).isEqualTo(paidAt.toLocalDate());
    }

    @Test
    @DisplayName("결제 접수 뒤 해지 예약해도 성공한 기간은 적용하며 예약과 자동갱신 중단은 유지한다")
    void keepsReservationWhenAcceptedPaymentSucceeds() {
        // given
        Membership value = pending();
        value.reserveCancellation(START);
        // when
        value.activateAfterFirstPayment(START.plusMinutes(1), END, END.plusDays(3));
        value.extendAfterRenewal(END.plusMonths(1), END.plusMonths(1).plusDays(3));
        // then
        assertThat(value.getCancelReservedAt()).isEqualTo(START);
        assertThat(value.isAutoRenew()).isFalse();
        assertThat(value.getGraceEndAt()).isNull();
        assertThat(value.getEndAt()).isEqualTo(END.plusMonths(1));
    }

    @Test
    @DisplayName("갱신은 종료일을 줄이지 않고 중복 성공이나 과거 결과로 기간을 덮어쓰지 않는다")
    void neverShortensRenewalAndKeepsGraceOnFailure() {
        // given
        Membership value = pending();
        value.activateAfterFirstPayment(START, END, END.plusDays(3));
        // when
        var extended = value.extendAfterRenewal(END.plusMonths(1), END.plusMonths(1).plusDays(3));
        var duplicate = value.extendAfterRenewal(END, END.plusDays(3));
        var failed = value.stopRenewal();
        // then
        assertThat(extended).isEqualTo(MembershipBillingResult.EXTENDED);
        assertThat(duplicate).isEqualTo(MembershipBillingResult.ALREADY_APPLIED);
        assertThat(failed).isEqualTo(MembershipBillingResult.RENEWAL_STOPPED);
        assertThat(value.getEndAt()).isEqualTo(END.plusMonths(1));
        assertThat(value.getGraceEndAt()).isEqualTo(END.plusMonths(1).plusDays(3));
        assertThat(value.stopRenewal()).isEqualTo(MembershipBillingResult.ALREADY_APPLIED);
        assertThat(value.isActive(END.plusMonths(1).plusDays(1))).isTrue();
    }

    @Test
    @DisplayName("미결제 종료는 반복해도 최초 시각을 유지하고 활성화된 이용권에는 충돌을 반환한다")
    void closesOnlyUnpaidMembership() {
        // given
        Membership unpaid = pending();
        Membership activated = pending();
        activated.activateAfterFirstPayment(START, END, END.plusDays(3));
        // when, then
        assertThat(unpaid.closeUnpaid(START.plusHours(1))).isEqualTo(MembershipBillingResult.CLOSED_UNPAID);
        assertThat(unpaid.closeUnpaid(START.plusHours(2))).isEqualTo(MembershipBillingResult.ALREADY_APPLIED);
        assertThat(unpaid.getTerminatedAt()).isEqualTo(START.plusHours(1));
        assertThat(unpaid.getActivatedAt()).isNull();
        assertThat(unpaid.activateAfterFirstPayment(START, END, null)).isEqualTo(MembershipBillingResult.CONFLICT);
        assertThat(activated.closeUnpaid(START)).isEqualTo(MembershipBillingResult.CONFLICT);
        assertThat(activated.getTerminatedAt()).isNull();
    }

    @Test
    @DisplayName("종료된 이용권이나 결제 대기에는 갱신 성공을 반영하지 않는다")
    void rejectsRenewalToUnpaidOrTerminated() {
        // given
        Membership value = pending();
        assertThat(value.extendAfterRenewal(END, null)).isEqualTo(MembershipBillingResult.CONFLICT);
        assertThat(value.stopRenewal()).isEqualTo(MembershipBillingResult.CONFLICT);
        value.activateAfterFirstPayment(START, END, END.plusDays(3));
        value.terminateAfterRefund(START.plusDays(1));
        // when, then
        assertThat(value.extendAfterRenewal(END.plusMonths(1), null)).isEqualTo(MembershipBillingResult.CONFLICT);
        assertThat(value.getGraceEndAt()).isNull();
    }

    @Test
    @DisplayName("카드 없는 전환 종료는 체험 종료 전에는 충돌이고 종료 시각부터 멱등하게 실행한다")
    void closesConversionOnlyAfterTrialEnd() {
        // given
        Membership value = trial();
        // when, then
        assertThat(value.closeConversion(START.plusDays(6))).isEqualTo(MembershipBillingResult.CONFLICT);
        assertThat(value.closeConversion(START.plusDays(7))).isEqualTo(MembershipBillingResult.CONVERSION_CLOSED);
        assertThat(value.closeConversion(START.plusDays(8))).isEqualTo(MembershipBillingResult.ALREADY_APPLIED);
        assertThat(value.getActivatedAt()).isEqualTo(START);
        assertThat(value.getCancelReservedAt()).isNull();
        assertThat(value.getTerminatedAt()).isNull();
    }

    @Test
    @DisplayName("전환 인계는 전환 대기에서만 가능하고 해지 예약 시각을 만들지 않는다")
    void handsOverOnlyPendingTrial() {
        // given
        Membership value = trial();
        // when, then
        assertThat(value.handOverConversion(START)).isEqualTo(MembershipBillingResult.CONFLICT);
        assertThat(value.handOverConversion(START.plusDays(7))).isEqualTo(MembershipBillingResult.CONVERSION_CLOSED);
        assertThat(value.handOverConversion(START.plusDays(7))).isEqualTo(MembershipBillingResult.CONFLICT);
        assertThat(value.getCancelReservedAt()).isNull();
        assertThat(value.isAutoRenew()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = MembershipSource.class, names = {"TRIAL", "ADMIN_GRANT"})
    @DisplayName("체험과 관리자 지급 경로로 결제 대기 유료 이용권을 만들지 않는다")
    void rejectsInvalidPendingSource(MembershipSource source) {
        // given, when, then
        assertThatThrownBy(() -> Membership.pendingPaid(Member.builder().build(), source, START, END, CONSENT))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("동의 누락과 잘못된 결제 기간은 반영 전에 거절한다")
    void rejectsInvalidConsentAndPeriod() {
        // given
        Membership value = pending();
        // when, then
        assertThatThrownBy(() -> Membership.pendingPaid(Member.builder().build(), MembershipSource.SUBSCRIBE,
                START, END, null)).isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.AUTOPAY_CONSENT_REQUIRED));
        assertThatThrownBy(() -> value.activateAfterFirstPayment(START, START, null))
                .isInstanceOf(BusinessException.class);
        assertThat(value.getActivatedAt()).isNull();
    }
}
