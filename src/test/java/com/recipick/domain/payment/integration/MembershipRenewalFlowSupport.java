package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.member.dto.MembershipConsent;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.domain.member.service.MembershipCancelReservationService;
import com.recipick.domain.payment.entity.BillingKey;
import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.facade.MembershipChargeDispatchProcessor;
import com.recipick.domain.payment.facade.MembershipChargeExecutor;
import com.recipick.domain.payment.facade.MembershipChargeRecoveryFacade;
import com.recipick.domain.payment.facade.MembershipChargeResultProcessor;
import com.recipick.domain.payment.facade.MembershipRenewalFacade;
import com.recipick.domain.payment.facade.MembershipRenewalProcessor;
import com.recipick.domain.payment.facade.MembershipSubscriptionFacade;
import com.recipick.domain.payment.repository.MembershipPaymentAttemptRepository;
import com.recipick.domain.payment.repository.MembershipPaymentRepository;
import com.recipick.domain.payment.service.MembershipSubscribeResult;
import com.recipick.domain.payment.service.MembershipSubscribeStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

// 갱신 흐름 테스트의 바닥. 갱신 직전 이용권은 엔티티 setter 없이 실제 첫 결제 흐름(바로 구독 → PG 스텁 PAID)으로 만든다.
// 기준 시각: 첫 결제 2027-01-31 10:03 → T = 02-28 10:03, G = 03-03 10:03, E = 03-31 10:03(원래 기준일 31일 유지).
// 시각은 MovableClock으로만 옮기고 정책값(24시간·3회·3일)은 줄이지 않는다. 시각 확인은 JPA 경로로만 읽는다
abstract class MembershipRenewalFlowSupport extends MembershipChargeFlowSupport {

    protected static final LocalDateTime FIRST_PAID_AT = LocalDateTime.of(2027, 1, 31, 10, 3);
    protected static final LocalDateTime T = LocalDateTime.of(2027, 2, 28, 10, 3);
    protected static final LocalDateTime G = T.plusDays(3);
    protected static final LocalDateTime E = LocalDateTime.of(2027, 3, 31, 10, 3);

    @Autowired protected MembershipSubscriptionFacade subscriptionFacade;
    @Autowired protected MembershipRenewalFacade renewalFacade;
    @Autowired protected MembershipRenewalProcessor renewalProcessor;
    @Autowired protected MembershipChargeExecutor executor;
    @Autowired protected MembershipChargeDispatchProcessor dispatchProcessor;
    @Autowired protected MembershipChargeResultProcessor resultProcessor;
    @Autowired protected MembershipChargeRecoveryFacade recovery;
    @Autowired protected MembershipCancelReservationService cancelReservation;
    @Autowired protected MembershipRepository memberships;
    @Autowired protected MembershipPaymentRepository payments;
    @Autowired protected MembershipPaymentAttemptRepository attempts;

    protected Member member;
    protected BillingKey cardA;

    @BeforeEach
    void setUpRenewalMember() {
        member = saveMember();
        cardA = saveActiveKey(member.getId());
    }

    // 첫 결제로 활성화된 유료 이용권. 끝나면 PG 스텁 기록을 비우고 시계를 T에 둔다
    protected Long activeMembership() {
        clock.fixAt(FIRST_PAID_AT);
        PORTONE.defaultPaid(FEE, FIRST_PAID_AT);
        MembershipSubscribeResult result = subscriptionFacade.subscribe(member.getId(),
            new MembershipConsent(FEE, "v1", FIRST_PAID_AT.minusDays(1)), "renew-" + UUID.randomUUID());
        assertThat(result.status()).isEqualTo(MembershipSubscribeStatus.PAID);
        assertThat(membership(result.membershipId()).getEndAt()).isEqualTo(T);
        PORTONE.reset();
        clock.fixAt(T);
        return result.membershipId();
    }

    protected Membership membership(Long membershipId) {
        return memberships.findById(membershipId).orElseThrow();
    }

    protected List<Long> renewalPaymentIds(Long membershipId) {
        return jdbc.queryForList("SELECT id FROM membership_payment WHERE membership_id = ? "
            + "AND grace_deadline_at IS NOT NULL ORDER BY id", Long.class, membershipId);
    }

    protected MembershipPayment onlyRenewalPayment(Long membershipId) {
        List<Long> ids = renewalPaymentIds(membershipId);
        assertThat(ids).hasSize(1);
        return payments.findById(ids.get(0)).orElseThrow();
    }

    protected List<MembershipPaymentAttempt> attemptsOf(Long paymentId) {
        return attempts.findByMembershipPaymentIdOrderByAttemptNoAsc(paymentId);
    }

    protected MembershipPaymentAttempt attempt(Long attemptId) {
        return attempts.findById(attemptId).orElseThrow();
    }

    protected Long billingKeyIdOf(Long attemptId) {
        return jdbc.queryForObject("SELECT billing_key_id FROM membership_payment_attempt WHERE id = ?", Long.class,
            attemptId);
    }

    // 그 시도의 SYSTEM 환불 REQUESTED 금액들
    protected List<Long> systemRefundAmounts(Long attemptId) {
        return jdbc.queryForList("SELECT amount FROM membership_refund WHERE membership_payment_attempt_id = ? "
            + "AND requested_by = 'SYSTEM' AND status = 'REQUESTED'", Long.class, attemptId);
    }

    protected long refundCountOfMember() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM membership_refund r JOIN membership_payment p "
                + "ON r.membership_payment_id = p.id JOIN membership m ON p.membership_id = m.id WHERE m.member_id = ?",
            Long.class, member.getId());
    }

    // 선점 TX만 실행해 PG에 갔을 수 있는 DISPATCHING을 만든다(POST는 보내지 않는다)
    protected String claim(Long attemptId) {
        return dispatchProcessor.claim(attemptId).ticket().orElseThrow().portonePaymentId();
    }
}
