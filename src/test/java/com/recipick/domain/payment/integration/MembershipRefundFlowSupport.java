package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.payment.dto.response.MembershipRefundResult;
import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.facade.MembershipRefundExecutionFacade;
import com.recipick.domain.payment.facade.MembershipRefundExecutionProcessor;
import com.recipick.domain.payment.facade.MembershipRefundFacade;
import com.recipick.domain.payment.facade.MembershipRefundRequestProcessor;
import com.recipick.domain.payment.facade.MembershipRefundResultProcessor;
import com.recipick.domain.payment.repository.MembershipRefundRepository;
import com.recipick.domain.payment.service.MembershipChargeOutcome;
import com.recipick.domain.payment.service.MembershipReconciliationTrigger;
import com.recipick.infra.portone.PortOnePaymentClient;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;

// 멤버십 환불 흐름 테스트의 바닥. 활성 이용권(첫 결제 PAID, 기간 1/31 10:03 → 2/28 10:03)과 갱신된 이용권(기간 T → E, 31일)을 실제
// 첫 결제·갱신 흐름(PG 스텁)으로 만든다. 취소 POST·GET은 스텁의 취소 시뮬레이션(simulateRefunds)으로 — POST 본문의 금액·reason으로
// 취소 내역을 만들고 GET이 그 목록·누적액을 돌려준다. 상점 ID는 테스트 설정(test-store-id)과 같다
abstract class MembershipRefundFlowSupport extends MembershipRenewalFlowSupport {

    protected static final String STORE_ID = "test-store-id";
    // 갱신 기간 T → E의 10일째(이용 10일 → 이용요금 1,258·환불 1,862)
    protected static final LocalDateTime DAY_10 = T.plusDays(9).plusHours(5);
    protected static final LocalDateTime RENEWAL_PAID_AT = T.plusMinutes(1);

    @Autowired protected MembershipRefundFacade refundFacade;
    @Autowired protected MembershipRefundExecutionFacade refundExecution;
    @Autowired protected MembershipRefundRequestProcessor refundRequests;
    @Autowired protected MembershipRefundResultProcessor refundResults;
    @Autowired protected MembershipRefundExecutionProcessor refundExecutions;
    @Autowired protected MembershipRefundRepository refunds;
    @Autowired protected PortOnePaymentClient portOnePaymentClient;

    // 첫 결제로 활성화되고 갱신까지 끝난 이용권(종료 E). 갱신 수납의 결제 ID에 취소 시뮬레이션을 건다. 시계는 T에 둔다
    protected Long renewedMembership() {
        Long membershipId = activeMembership();
        Long attemptId = renewalProcessor.accept(member.getId(), membershipId).attemptId();
        String paymentId = claim(attemptId);
        PORTONE.getPaymentPaid(paymentId, FEE, "KRW", RENEWAL_PAID_AT);
        clock.fixAt(T.plusMinutes(2));
        MembershipChargeOutcome outcome = executor.reconcile(member.getId(), attemptId,
            MembershipReconciliationTrigger.VERIFIED_WEBHOOK);
        assertThat(outcome).isEqualTo(MembershipChargeOutcome.EXTENDED);
        assertThat(membership(membershipId).getEndAt()).isEqualTo(E);
        PORTONE.reset();
        PORTONE.simulateRefunds(paymentId, STORE_ID, FEE, RENEWAL_PAID_AT);
        clock.fixAt(T);
        return membershipId;
    }

    // 첫 결제만 끝난 활성 이용권의 결제 ID에 취소 시뮬레이션을 건다
    protected String simulateFirstPayment(Long membershipId) {
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.simulateRefunds(paymentId, STORE_ID, FEE, FIRST_PAID_AT);
        return paymentId;
    }

    protected MembershipRefundResult requestRefund(Long membershipId, String key) {
        return refundFacade.requestMemberRefund(member.getId(), membershipId, key);
    }

    protected String newKey() {
        return "term-" + UUID.randomUUID();
    }

    protected MembershipRefund refund(Long refundId) {
        return refunds.findById(refundId).orElseThrow();
    }

    // 이 회원의 환불 행 전부(ID 순)
    protected List<MembershipRefund> refundsOfMember() {
        List<Long> ids = jdbc.queryForList("SELECT r.id FROM membership_refund r JOIN membership_payment_attempt a "
            + "ON r.membership_payment_attempt_id = a.id JOIN membership_payment p ON a.membership_payment_id = p.id "
            + "JOIN membership m ON p.membership_id = m.id WHERE m.member_id = ? ORDER BY r.id", Long.class, member.getId());
        return ids.stream().map(this::refund).toList();
    }

    // 정상 반영된 PAID 시도(이용권의 최신 반영 회차)의 PortOne 결제 ID
    protected String appliedPaymentId(Long membershipId) {
        return jdbc.queryForObject("SELECT a.portone_payment_id FROM membership_payment_attempt a "
            + "JOIN membership_payment p ON a.membership_payment_id = p.id WHERE p.membership_id = ? AND a.status = 'PAID' "
            + "AND a.refund_required_at IS NULL AND p.membership_applied_at IS NOT NULL "
            + "ORDER BY p.period_start_date DESC, p.id DESC LIMIT 1", String.class, membershipId);
    }

    protected Long appliedAttemptId(Long membershipId) {
        return jdbc.queryForObject("SELECT a.id FROM membership_payment_attempt a "
            + "JOIN membership_payment p ON a.membership_payment_id = p.id WHERE p.membership_id = ? AND a.status = 'PAID' "
            + "AND a.refund_required_at IS NULL AND p.membership_applied_at IS NOT NULL "
            + "ORDER BY p.period_start_date DESC, p.id DESC LIMIT 1", Long.class, membershipId);
    }

    protected String attemptOpsReason(Long attemptId) {
        return jdbc.queryForObject("SELECT ops_review_reason FROM membership_payment_attempt WHERE id = ?", String.class,
            attemptId);
    }

    protected void withdraw() {
        jdbc.update("UPDATE member SET withdrawn_at = ? WHERE id = ?", LocalDateTime.now(clock), member.getId());
    }

    // 다른 트랜잭션이 회원 행 잠금을 쥔 채 기다리게 한다. 돌려준 래치를 내리면 잠금이 풀린다(잠금 대기 중 시각이 바뀌는 상황 재현)
    protected HeldLock holdMemberLock() throws InterruptedException {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService holder = Executors.newSingleThreadExecutor();
        Future<?> future = holder.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT id FROM member WHERE id = ? FOR UPDATE", Long.class, member.getId());
            locked.countDown();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        return new HeldLock(release, future, holder);
    }

    protected record HeldLock(CountDownLatch release, Future<?> future, ExecutorService holder) {
        public void releaseAndWait() throws Exception {
            release.countDown();
            future.get(10, TimeUnit.SECONDS);
            holder.shutdownNow();
        }
    }
}
