package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.dto.MembershipConsent;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class MembershipOverdueRenewalIntegrationTest extends IntegrationTestSupport {
    @Autowired MembershipBillingService billing;
    @Autowired MemberRepository members;
    @Autowired MembershipRepository memberships;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 12, 0);
    private static final LocalDateTime START = NOW.minusMonths(1);
    private static final MembershipConsent CONSENT = new MembershipConsent(3900L, "v1", START);

    private Member member() {
        String suffix = UUID.randomUUID().toString().substring(0, 20);
        return members.saveAndFlush(Member.builder().email(suffix + "@overdue.test").phone(suffix).passwordHash("hash").build());
    }

    private Membership paid(Member member) {
        Membership value = Membership.pendingPaid(member, MembershipSource.SUBSCRIBE, START, NOW.minusDays(3), CONSENT);
        value.activateAfterFirstPayment(START, NOW.minusDays(3), NOW);
        return value;
    }

    @ParameterizedTest
    @EnumSource(value = MembershipSource.class, names = {"SUBSCRIBE", "TRIAL_CONVERT", "ADMIN_GRANT"})
    @DisplayName("마감 누락 조회는 자동갱신 값과 무관하게 관리자 지급을 제외하고 구독·체험 전환만 포함한다")
    void restrictsSourcesToSubscriptionAndConversion(MembershipSource source) {
        // given
        Membership value = Membership.builder().member(member()).type(MembershipType.PAID).source(source)
                .startAt(START).activatedAt(START).endAt(NOW.minusDays(3)).autoRenew(true).build();
        ReflectionTestUtils.setField(value, "graceEndAt", NOW);
        memberships.saveAndFlush(value);
        // when
        var targets = billing.findOverdueRenewalTargets(NOW, value.getId() - 1, 10);
        // then
        if (source == MembershipSource.ADMIN_GRANT) {
            assertThat(targets).extracting(row -> row.membershipId()).doesNotContain(value.getId());
        } else {
            assertThat(targets).extracting(row -> row.membershipId()).contains(value.getId());
        }
    }

    @Test
    @DisplayName("해지 예약 시각이 있으면 다른 갱신 조건이 맞아도 마감 누락 대상에서 제외한다")
    void excludesReservedEvenWhenAutoRenewAndGraceRemain() {
        // given
        Membership value = paid(member());
        ReflectionTestUtils.setField(value, "cancelReservedAt", NOW.minusDays(1));
        memberships.saveAndFlush(value);
        // when, then
        assertThat(billing.findOverdueRenewalTargets(NOW, value.getId() - 1, 10))
                .extracting(row -> row.membershipId()).doesNotContain(value.getId());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    @DisplayName("유예 종료 시각 직전은 갱신 대상이고 해당 시각부터 유예 종료 대상으로 조회한다")
    void splitsRenewalAndOverdueAtGraceEnd(int seconds) {
        // given
        Membership value = memberships.saveAndFlush(paid(member()));
        LocalDateTime at = NOW.plusSeconds(seconds);
        // when
        var overdue = billing.findOverdueRenewalTargets(at, null, 10);
        var renewal = billing.findRenewalTargets(at, null, 10);
        // then
        if (seconds < 0) {
            assertThat(overdue).extracting(row -> row.membershipId()).doesNotContain(value.getId());
            assertThat(renewal).extracting(row -> row.membershipId()).contains(value.getId());
        } else {
            assertThat(overdue).extracting(row -> row.membershipId()).contains(value.getId());
            assertThat(renewal).extracting(row -> row.membershipId()).doesNotContain(value.getId());
        }
    }

    @Test
    @DisplayName("유예 종료 대상은 이용권 ID 오름차순으로 커서 다음 페이지와 소유 회원 ID를 반환한다")
    void pagesInIdOrderAndIncludesOwner() {
        // given
        Member owner = member();
        Membership first = memberships.saveAndFlush(paid(owner));
        Membership second = memberships.saveAndFlush(paid(owner));
        // when
        var page1 = billing.findOverdueRenewalTargets(NOW, first.getId() - 1, 1);
        var page2 = billing.findOverdueRenewalTargets(NOW, first.getId(), 1);
        // then
        assertThat(page1).hasSize(1);
        assertThat(page1.get(0).membershipId()).isEqualTo(first.getId());
        assertThat(page1.get(0).memberId()).isEqualTo(owner.getId());
        assertThat(page2).extracting(row -> row.membershipId()).containsExactly(second.getId());
    }

    @Test
    @DisplayName("체험·미활성·갱신 중단·해지 예약·즉시 종료·유예 없는 이용권은 종료 대상에서 제외한다")
    void excludesIneligibleLifecycleStates() {
        // given
        Member owner = member();
        Membership eligible = memberships.saveAndFlush(paid(owner));
        Membership stopped = paid(owner);
        stopped.stopRenewal();
        memberships.saveAndFlush(stopped);
        Membership reserved = paid(owner);
        reserved.reserveCancellation(NOW.minusDays(1));
        memberships.saveAndFlush(reserved);
        Membership terminated = paid(owner);
        terminated.terminateAfterRefund(NOW.minusDays(1));
        memberships.saveAndFlush(terminated);
        Membership pending = memberships.saveAndFlush(Membership.pendingPaid(owner, MembershipSource.SUBSCRIBE,
                START, NOW.minusDays(3), CONSENT));
        Membership noGrace = memberships.saveAndFlush(Membership.builder().member(owner).type(MembershipType.PAID)
                .source(MembershipSource.SUBSCRIBE).startAt(START).activatedAt(START).endAt(NOW.minusDays(3))
                .autoRenew(true).build());
        Membership trial = memberships.saveAndFlush(Membership.builder().member(owner).type(MembershipType.TRIAL)
                .source(MembershipSource.TRIAL).startAt(START).activatedAt(START).endAt(START.plusDays(7))
                .autoRenew(true).build());
        // when
        var targets = billing.findOverdueRenewalTargets(NOW, eligible.getId() - 1, 20);
        // then
        assertThat(targets).extracting(row -> row.membershipId()).contains(eligible.getId())
                .doesNotContain(stopped.getId(), reserved.getId(), terminated.getId(), pending.getId(), noGrace.getId(), trial.getId());
    }

    @Test
    @DisplayName("대상 조회는 기준시각 누락·음수 커서·0 이하 페이지 크기를 거절한다")
    void rejectsInvalidQueryArguments() {
        // given, when, then
        assertThatThrownBy(() -> billing.findOverdueRenewalTargets(null, 0L, 10)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> billing.findOverdueRenewalTargets(NOW, -1L, 10)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> billing.findOverdueRenewalTargets(NOW, 0L, 0)).isInstanceOf(BusinessException.class);
    }
}
