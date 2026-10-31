package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.dto.MembershipBillingResult;
import com.recipick.domain.member.dto.MembershipConsent;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Transactional
class MembershipBillingIntegrationTest extends IntegrationTestSupport {
    @Autowired MembershipBillingService billing;
    @Autowired MembershipService service;
    @Autowired MembershipRepository memberships;
    @Autowired MemberRepository members;
    @Autowired MemberService memberService;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 12, 0);
    private static final MembershipConsent CONSENT = new MembershipConsent(3900L, "v1", NOW.minusDays(7));

    private Member member() {
        String suffix = UUID.randomUUID().toString().substring(0, 20);
        return members.saveAndFlush(Member.builder().email(suffix + "@billing.test")
                .phone(suffix).name("결제 결과 전").passwordHash("hash").build());
    }

    private Membership trial(Member member, LocalDateTime end) {
        return memberships.saveAndFlush(Membership.builder().member(member).type(MembershipType.TRIAL)
                .source(MembershipSource.TRIAL).startAt(end.minusDays(7)).activatedAt(end.minusDays(7)).endAt(end)
                .autoRenew(true).consentedAmount(CONSENT.consentedAmount()).consentTextVersion(CONSENT.consentTextVersion())
                .autopayConsentedAt(CONSENT.autopayConsentedAt()).build());
    }

    @Test
    @DisplayName("구독 대기 생성과 첫 결제 반영은 같은 이용권을 잠그고 DTO로 전달하며 반복 반영에 안전하다")
    void createsLocksAndActivatesPendingSubscription() {
        // given
        Member member = member();
        memberService.lockActiveMember(member.getId());
        Long id = billing.createPendingSubscription(member.getId(), CONSENT, NOW);
        var pending = billing.getBillingMembershipForUpdate(member.getId(), id);
        // when
        var first = billing.applyFirstPaymentPaid(id, NOW.plusMinutes(1), NOW.plusMonths(1), NOW.plusMonths(1).plusDays(3));
        var repeated = billing.applyFirstPaymentPaid(id, NOW.plusMinutes(2), NOW.plusMonths(2), null);
        memberships.flush();
        entityManager.clear();
        // then
        assertThat(pending.activatedAt()).isNull();
        assertThat(pending.consent()).isEqualTo(CONSENT);
        assertThat(first).isEqualTo(MembershipBillingResult.ACTIVATED);
        assertThat(repeated).isEqualTo(MembershipBillingResult.ALREADY_APPLIED);
        Membership loaded = memberships.findById(id).orElseThrow();
        assertThat(loaded.getStartAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(loaded.getEndAt()).isEqualTo(NOW.plusMonths(1));
        assertThat(loaded.getGraceEndAt()).isEqualTo(NOW.plusMonths(1).plusDays(3));
        assertThat(billing.findMembershipIds(member.getId())).containsExactly(id);
    }

    @Test
    @DisplayName("체험 전환은 기존 행을 보존하고 동의를 복사한 별도 유료 대기 행을 만든다")
    void createsSeparateConversionAndPreservesTrialHistory() {
        // given
        Member member = member();
        Membership trial = trial(member, NOW);
        memberService.lockActiveMember(member.getId());
        billing.getBillingMembershipForUpdate(member.getId(), trial.getId());
        // when
        Long paidId = billing.createPendingConversion(member.getId(), trial.getId(), NOW);
        var pending = billing.getBillingMembershipForUpdate(member.getId(), paidId);
        // then
        assertThat(trial.isAutoRenew()).isFalse();
        assertThat(trial.getCancelReservedAt()).isNull();
        assertThat(trial.getActivatedAt()).isEqualTo(NOW.minusDays(7));
        assertThat(pending.source()).isEqualTo(MembershipSource.TRIAL_CONVERT);
        assertThat(pending.consent()).isEqualTo(CONSENT);
        assertThat(pending.activatedAt()).isNull();
        assertThat(billing.findMembershipIds(member.getId())).containsExactly(trial.getId(), paidId);
        assertThatThrownBy(() -> billing.createPendingConversion(member.getId(), trial.getId(), NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("타인 이용권은 잠금 조회로 노출하지 않고 활성·대기 이용권 중복 구독을 거절한다")
    void protectsOwnershipAndDuplicateSubscription() {
        // given
        Member owner = member();
        Member other = member();
        memberService.lockActiveMember(owner.getId());
        Long id = billing.createPendingSubscription(owner.getId(), CONSENT, NOW);
        // when, then
        assertThatThrownBy(() -> billing.getBillingMembershipForUpdate(other.getId(), id))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.ACTIVE_MEMBERSHIP_NOT_FOUND));
        assertThatThrownBy(() -> billing.createPendingSubscription(owner.getId(), CONSENT, NOW))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBERSHIP_ALREADY_ACTIVE));
        assertThat(billing.existsBlockingMembership(owner.getId(), NOW)).isTrue();
    }

    @Test
    @DisplayName("갱신 성공·중단·환불 종료는 반복 호출에도 한 번만 변경한다")
    void appliesRenewalAndRefundIdempotently() {
        // given
        Member member = member();
        memberService.lockActiveMember(member.getId());
        Long id = billing.createPendingSubscription(member.getId(), CONSENT, NOW);
        billing.getBillingMembershipForUpdate(member.getId(), id);
        billing.applyFirstPaymentPaid(id, NOW, NOW.plusMonths(1), NOW.plusMonths(1).plusDays(3));
        // when, then
        assertThat(billing.applyRenewalPaid(id, NOW.plusMonths(2), NOW.plusMonths(2).plusDays(3)))
                .isEqualTo(MembershipBillingResult.EXTENDED);
        assertThat(billing.applyRenewalPaid(id, NOW.plusMonths(2), null)).isEqualTo(MembershipBillingResult.ALREADY_APPLIED);
        assertThat(billing.stopRenewal(id)).isEqualTo(MembershipBillingResult.RENEWAL_STOPPED);
        assertThat(billing.stopRenewal(id)).isEqualTo(MembershipBillingResult.ALREADY_APPLIED);
        assertThat(memberships.findById(id).orElseThrow().getGraceEndAt()).isEqualTo(NOW.plusMonths(2).plusDays(3));
        assertThat(billing.terminateAfterRefund(id, NOW.plusDays(1))).isEqualTo(MembershipBillingResult.TERMINATED);
        assertThat(billing.terminateAfterRefund(id, NOW.plusDays(2))).isEqualTo(MembershipBillingResult.ALREADY_APPLIED);
        assertThat(memberships.findById(id).orElseThrow().getTerminatedAt()).isEqualTo(NOW.plusDays(1));
    }

    @Test
    @DisplayName("첫 결제 실패 이력도 전체 ID에 포함하지만 활성 이력과 체험 자격 차단으로 세지 않는다")
    void includesFailedIdsWithoutLosingTrialEligibility() {
        // given
        Member member = member();
        memberService.lockActiveMember(member.getId());
        Long id = billing.createPendingSubscription(member.getId(), CONSENT, NOW);
        billing.getBillingMembershipForUpdate(member.getId(), id);
        // when, then
        assertThat(billing.applyFirstPaymentFailed(id, NOW)).isEqualTo(MembershipBillingResult.CLOSED_UNPAID);
        assertThat(billing.applyFirstPaymentFailed(id, NOW.plusMinutes(1))).isEqualTo(MembershipBillingResult.ALREADY_APPLIED);
        assertThat(billing.findMembershipIds(member.getId())).containsExactly(id);
        assertThat(service.isTrialEligible(member.getId())).isTrue();
        assertThat(billing.existsBlockingMembership(member.getId(), NOW)).isFalse();
    }

    @Test
    @DisplayName("대상 조회는 PK 커서로 페이지를 이어가고 만료·해지 예약·미활성 행을 갱신 대상에서 제외한다")
    void pagesTargetsAndFiltersRenewalBoundaries() {
        // given
        Member member = member();
        Membership first = trial(member, NOW);
        Membership second = trial(member, NOW.minusDays(1));
        Membership cancelled = trial(member, NOW);
        cancelled.reserveCancellation(NOW);
        Membership renewal = Membership.pendingPaid(member, MembershipSource.SUBSCRIBE, NOW.minusMonths(1), NOW, CONSENT);
        renewal.activateAfterFirstPayment(NOW.minusMonths(1), NOW, NOW.plusDays(3));
        memberships.saveAndFlush(renewal);
        Membership unpaid = memberships.saveAndFlush(Membership.pendingPaid(member, MembershipSource.SUBSCRIBE,
                NOW.minusMonths(1), NOW, CONSENT));
        // when
        var page1 = billing.findTrialConversionTargets(NOW, null, 1);
        var page2 = billing.findTrialConversionTargets(NOW, first.getId(), 1);
        // then
        assertThat(page1).extracting(value -> value.membershipId()).containsExactly(first.getId());
        assertThat(page2).extracting(value -> value.membershipId()).containsExactly(second.getId());
        assertThat(billing.findRenewalTargets(NOW.minusSeconds(1), null, 10)).isEmpty();
        assertThat(billing.findRenewalTargets(NOW, null, 10)).extracting(value -> value.membershipId())
                .containsExactly(renewal.getId()).doesNotContain(unpaid.getId());
        assertThat(billing.findRenewalTargets(NOW.plusDays(3), null, 10)).isEmpty();
        assertThat(billing.closeTrialConversion(first.getId(), NOW)).isEqualTo(MembershipBillingResult.CONVERSION_CLOSED);
        assertThat(billing.closeTrialConversion(first.getId(), NOW)).isEqualTo(MembershipBillingResult.ALREADY_APPLIED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("변경 계약은 바깥 트랜잭션이 없으면 실행하지 않는다")
    void requiresExistingTransaction() {
        // given
        List<Runnable> calls = List.of(
                () -> billing.getBillingMembershipForUpdate(1L, 1L),
                () -> billing.createPendingSubscription(1L, CONSENT, NOW),
                () -> billing.createPendingConversion(1L, 1L, NOW),
                () -> billing.applyFirstPaymentPaid(1L, NOW, NOW.plusMonths(1), null),
                () -> billing.applyFirstPaymentFailed(1L, NOW),
                () -> billing.applyRenewalPaid(1L, NOW.plusMonths(1), null),
                () -> billing.stopRenewal(1L),
                () -> billing.closeTrialConversion(1L, NOW),
                () -> billing.terminateAfterRefund(1L, NOW));
        // when, then
        calls.forEach(call -> assertThatThrownBy(call::run).isInstanceOf(IllegalTransactionStateException.class));
    }

    @Test
    @Transactional(readOnly = true)
    @DisplayName("읽기 전용 바깥 트랜잭션에서는 잠금·생성·반영을 모두 거절한다")
    void rejectsReadOnlyTransaction() {
        // given
        List<Runnable> calls = List.of(
                () -> billing.getBillingMembershipForUpdate(1L, 1L),
                () -> billing.createPendingSubscription(1L, CONSENT, NOW),
                () -> billing.createPendingConversion(1L, 1L, NOW),
                () -> billing.applyFirstPaymentPaid(1L, NOW, NOW.plusMonths(1), null),
                () -> billing.applyFirstPaymentFailed(1L, NOW),
                () -> billing.applyRenewalPaid(1L, NOW.plusMonths(1), null),
                () -> billing.stopRenewal(1L),
                () -> billing.closeTrialConversion(1L, NOW),
                () -> billing.terminateAfterRefund(1L, NOW));
        // when, then
        calls.forEach(call -> assertThatThrownBy(call::run).isInstanceOf(IllegalStateException.class));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("반영 예외는 호출자의 선행 DB 변경과 이용권 생성까지 함께 롤백한다")
    void rollsBackCallerChangesWhenApplyFails() {
        // given
        Member member = member();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            // when
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                memberService.lockActiveMember(member.getId());
                Long id = billing.createPendingSubscription(member.getId(), CONSENT, NOW);
                billing.getBillingMembershipForUpdate(member.getId(), id);
                jdbc.update("UPDATE member SET name = ? WHERE id = ?", "결제 결과 저장", member.getId());
                billing.applyFirstPaymentPaid(id, NOW, NOW, null);
            })).isInstanceOf(BusinessException.class);
            // then
            assertThat(members.findById(member.getId()).orElseThrow().getName()).isEqualTo("결제 결과 전");
            assertThat(billing.findMembershipIds(member.getId())).isEmpty();
        } finally {
            members.deleteById(member.getId());
        }
    }
}
