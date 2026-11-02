package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.payment.dto.response.MembershipRefundResult;
import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 즉시 해지 환불 접수 — 기간·자격·멱등·재요청·0원·대상 회차 선택·잠금 뒤 시각. 실제 MySQL + PortOne 스텁(취소 시뮬레이션)
class MembershipRefundRequestIntegrationTest extends MembershipRefundFlowSupport {

    @Test
    @DisplayName("갱신 두 번째 달의 10일째 요청은 최초 가입일이 아니라 갱신 기간(31일) 기준으로 1,862원을 환불하고 완료와 종료를 함께 커밋한다")
    void secondMonthUsesRenewalPeriodNotSubscriptionStart() {
        // given
        Long membershipId = renewedMembership();
        clock.fixAt(DAY_10);

        // when
        MembershipRefundResult result = requestRefund(membershipId, newKey());

        // then
        assertThat(result.refundStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(result.paidAmount()).isEqualTo(FEE);
        assertThat(result.periodDays()).isEqualTo(31);
        assertThat(result.usedDays()).isEqualTo(10);
        assertThat(result.usedDayFee()).isEqualTo(1_258L);
        assertThat(result.penaltyAmount()).isEqualTo(780L);
        assertThat(result.refundAmount()).isEqualTo(1_862L);
        assertThat(result.membershipTerminated()).isTrue();
        assertThat(result.reviewRequired()).isFalse();
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(DAY_10);
        assertThat(membership(membershipId).isAutoRenew()).isFalse();
        String paymentId = appliedPaymentId(membershipId);
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
        assertThat(PORTONE.cancelBodies(paymentId).get(0))
            .contains("\"amount\":1862").contains("\"currentCancellableAmount\":3900").contains("\"requester\":\"CUSTOMER\"")
            .contains("\"storeId\":\"test-store-id\"");
        MembershipRefund refund = refund(result.refundId());
        assertThat(refund.getPortoneCancellationId()).isEqualTo("cxl_1");
        assertThat(refund.getPgRequestReason()).isEqualTo("RECIPICK_MEMBERSHIP_REFUND_V1_" + refund.getId() + "_"
            + refund.getPgIdempotencyKey());
        assertThat(PORTONE.cancelIdempotencyKeys(paymentId)).containsExactly("\"" + refund.getPgIdempotencyKey() + "\"");
        assertThat(refund.getFirstDispatchedAt()).isEqualTo(DAY_10);
        assertThat(refund.getLeaseExpiresAt()).isNull();
        assertThat(refund.getNextCheckAt()).isNull();
    }

    @Test
    @DisplayName("미정산 갱신 회차가 있으면 MEMBERSHIP-010으로 보류하고 환불 행을 만들지 않는다")
    void unresolvedRenewalDefersTermination() {
        // given
        Long membershipId = activeMembership();
        renewalProcessor.accept(member.getId(), membershipId);
        simulateFirstPayment(membershipId);

        // when & then
        assertThatThrownBy(() -> requestRefund(membershipId, newKey()))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(MemberErrorCode.CANCEL_PAYMENT_IN_PROGRESS);
        assertThat(refundsOfMember()).isEmpty();
        assertThat(PORTONE.totalCancelCount()).isZero();
        assertThat(membership(membershipId).getTerminatedAt()).isNull();
    }

    @Test
    @DisplayName("미정산 없이 혜택 유예 중이면 0원 환불 완료·이용권 종료이고 PG 호출(GET·POST)이 없다")
    void graceOnlyMembershipCompletesWithZeroRefund() {
        // given
        Long membershipId = activeMembership();
        String paymentId = simulateFirstPayment(membershipId);
        clock.fixAt(T.plusHours(1));

        // when
        MembershipRefundResult result = requestRefund(membershipId, newKey());

        // then
        assertThat(result.refundStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(result.refundAmount()).isZero();
        assertThat(result.usedDays()).isEqualTo(28);
        assertThat(result.periodDays()).isEqualTo(28);
        assertThat(result.membershipTerminated()).isTrue();
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(T.plusHours(1));
        assertThat(PORTONE.getCount(paymentId)).isZero();
        assertThat(PORTONE.cancelCount(paymentId)).isZero();
        assertThat(refund(result.refundId()).getPortoneCancellationId()).isNull();
    }

    @Test
    @DisplayName("0원 완료를 같은 키로 재전송하면 기존 결과를 돌려주고 종료·PG 호출이 반복되지 않는다")
    void zeroRefundReplayIsIdempotent() {
        // given
        Long membershipId = activeMembership();
        String paymentId = simulateFirstPayment(membershipId);
        clock.fixAt(T.plusHours(1));
        String key = newKey();
        MembershipRefundResult first = requestRefund(membershipId, key);
        clock.fixAt(T.plusHours(2));

        // when
        MembershipRefundResult replay = requestRefund(membershipId, key);

        // then
        assertThat(replay.refundId()).isEqualTo(first.refundId());
        assertThat(replay.completedAt()).isEqualTo(T.plusHours(1));
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(T.plusHours(1));
        assertThat(refundsOfMember()).hasSize(1);
        assertThat(PORTONE.totalGetCount()).isZero();
        assertThat(PORTONE.cancelCount(paymentId)).isZero();
    }

    @Test
    @DisplayName("같은 키 순차 재요청은 기존 결과(같은 환불 ID)를 돌려주고 새 계산·새 행·PG 호출이 없다")
    void sameKeyReturnsExistingResult() {
        // given
        Long membershipId = renewedMembership();
        clock.fixAt(DAY_10);
        String key = newKey();
        MembershipRefundResult first = requestRefund(membershipId, key);
        String paymentId = appliedPaymentId(membershipId);
        int getsAfterFirst = PORTONE.getCount(paymentId);
        clock.fixAt(DAY_10.plusDays(3));

        // when
        MembershipRefundResult again = requestRefund(membershipId, key);

        // then
        assertThat(again.refundId()).isEqualTo(first.refundId());
        assertThat(again.usedDays()).isEqualTo(10);
        assertThat(again.refundAmount()).isEqualTo(1_862L);
        assertThat(refundsOfMember()).hasSize(1);
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(1);
        assertThat(PORTONE.getCount(paymentId)).isEqualTo(getsAfterFirst);
    }

    @Test
    @DisplayName("같은 키로 다른 이용권을 보내면 COMMON-011이다")
    void sameKeyDifferentMembershipIsRejected() {
        // given
        Long membershipId = renewedMembership();
        clock.fixAt(DAY_10);
        String key = newKey();
        requestRefund(membershipId, key);

        // when & then
        assertThatThrownBy(() -> requestRefund(membershipId + 1_000_000L, key))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH);
    }

    @Test
    @DisplayName("환불이 REQUESTED(결과 불명)인 동안 다른 키의 요청은 MEMBERSHIP-010으로 거절되고 새 환불이 생기지 않는다")
    void anotherKeyWhileRequestedIsRejected() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.getPaymentServerError(paymentId);
        clock.fixAt(DAY_10);
        MembershipRefundResult first = requestRefund(membershipId, newKey());

        // when & then
        assertThat(first.refundStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(first.membershipTerminated()).isFalse();
        assertThatThrownBy(() -> requestRefund(membershipId, newKey()))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(MemberErrorCode.CANCEL_PAYMENT_IN_PROGRESS);
        assertThat(refundsOfMember()).hasSize(1);
        assertThat(PORTONE.cancelCount(paymentId)).isZero();
        assertThat(membership(membershipId).getTerminatedAt()).isNull();
    }

    @Test
    @DisplayName("명확한 FAILED 뒤 새 키의 요청은 새 날짜 기준으로 다시 계산한 새 행을 만든다")
    void newKeyAfterFailedRecalculatesByNewDate() {
        // given
        Long membershipId = renewedMembership();
        String paymentId = appliedPaymentId(membershipId);
        PORTONE.simulatePostStatus(paymentId, "FAILED");
        clock.fixAt(DAY_10);
        MembershipRefundResult failed = requestRefund(membershipId, newKey());
        PORTONE.simulatePostStatus(paymentId, "SUCCEEDED");
        clock.fixAt(DAY_10.plusDays(2));

        // when
        MembershipRefundResult retried = requestRefund(membershipId, newKey());

        // then
        assertThat(failed.refundStatus()).isEqualTo(MembershipRefundStatus.FAILED);
        assertThat(failed.membershipTerminated()).isFalse();
        assertThat(refund(failed.refundId()).getFailureReason()).isEqualTo("PG_CANCEL_FAILED");
        assertThat(retried.refundId()).isNotEqualTo(failed.refundId());
        assertThat(retried.usedDays()).isEqualTo(12);
        assertThat(retried.refundStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(membership(membershipId).getTerminatedAt()).isEqualTo(DAY_10.plusDays(2));
        assertThat(PORTONE.cancelCount(paymentId)).isEqualTo(2);
        assertThat(PORTONE.cancelIdempotencyKeys(paymentId)).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("회원 잠금을 기다리는 사이 자정을 넘기면 잠금 뒤 요청일로 계산한다")
    void lockWaitCrossingMidnightUsesPostLockDate() throws Exception {
        // given
        Long membershipId = renewedMembership();
        clock.fixAt(T.plusDays(9).withHour(23).withMinute(59));
        HeldLock held = holdMemberLock();
        ExecutorService requester = Executors.newSingleThreadExecutor();
        Future<MembershipRefundResult> pending = requester.submit(() -> requestRefund(membershipId, newKey()));
        Thread.sleep(500);
        clock.fixAt(T.plusDays(10).withHour(0).withMinute(1));

        // when
        held.releaseAndWait();
        MembershipRefundResult result = pending.get(20, TimeUnit.SECONDS);
        requester.shutdownNow();

        // then
        assertThat(result.usedDays()).isEqualTo(11);
        assertThat(result.requestedAt()).isEqualTo(T.plusDays(10).withHour(0).withMinute(1));
        assertThat(result.refundStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
    }

    @Test
    @DisplayName("회원 잠금을 기다리는 사이 혜택 기간이 끝나면 잠금 뒤 자격으로 판단해 MEMBERSHIP-008이다")
    void lockWaitPastBenefitEndUsesPostLockEligibility() throws Exception {
        // given
        Long membershipId = renewedMembership();
        clock.fixAt(E.plusDays(3).minusMinutes(1));
        HeldLock held = holdMemberLock();
        ExecutorService requester = Executors.newSingleThreadExecutor();
        Future<MembershipRefundResult> pending = requester.submit(() -> requestRefund(membershipId, newKey()));
        Thread.sleep(500);
        clock.fixAt(E.plusDays(3));

        // when
        held.releaseAndWait();

        // then
        assertThatThrownBy(() -> pending.get(20, TimeUnit.SECONDS))
            .hasCauseInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e.getCause()).getErrorCode())
            .isEqualTo(MemberErrorCode.NOT_TERMINABLE);
        requester.shutdownNow();
        assertThat(refundsOfMember()).isEmpty();
    }

    @Test
    @DisplayName("ID가 더 큰 과거 기간 회차가 있어도 현재 기간(이용권 종료와 맞는) 회차를 환불 대상으로 고른다")
    void latePastPeriodRoundIsNotChosenByMaxId() {
        // given
        Long membershipId = renewedMembership();
        Long firstPaymentId = jdbc.queryForObject("SELECT id FROM membership_payment WHERE membership_id = ? "
            + "AND grace_deadline_at IS NULL", Long.class, membershipId);
        // 같은 이용권에 기간이 더 이른(과거) 반영 회차가 더 큰 ID로 뒤늦게 남은 상태(기간 시작일은 UNIQUE라 더 이른 날짜로 둔다)
        jdbc.update("INSERT INTO membership_payment (membership_id, amount, status, billing_date, period_start_date, "
            + "period_end_date, paid_at, membership_applied_at, created_at, updated_at) "
            + "SELECT membership_id, amount, status, DATE_SUB(billing_date, INTERVAL 1 MONTH), "
            + "DATE_SUB(period_start_date, INTERVAL 1 MONTH), DATE_SUB(period_end_date, INTERVAL 1 MONTH), "
            + "DATE_SUB(paid_at, INTERVAL 1 MONTH), membership_applied_at, created_at, updated_at "
            + "FROM membership_payment WHERE id = ?", firstPaymentId);
        clock.fixAt(DAY_10);

        // when
        MembershipRefundResult result = requestRefund(membershipId, newKey());

        // then
        assertThat(result.periodDays()).isEqualTo(31);
        assertThat(result.usedDays()).isEqualTo(10);
        assertThat(result.refundAmount()).isEqualTo(1_862L);
        assertThat(result.membershipPaymentId()).isEqualTo(onlyRenewalPayment(membershipId).getId());
    }

    @Test
    @DisplayName("정상 반영 PAID 시도가 정확히 하나가 아니면(결제 시각 불일치) BILLING-022로 거절하고 시도를 차단하며 환불·PG 호출이 없다")
    void appliedPaidAttemptMismatchBlocksWithoutRefund() {
        // given
        Long membershipId = renewedMembership();
        Long attemptId = appliedAttemptId(membershipId);
        jdbc.update("UPDATE membership_payment_attempt SET pg_paid_at = DATE_ADD(pg_paid_at, INTERVAL 1 MICROSECOND) "
            + "WHERE id = ?", attemptId);
        clock.fixAt(DAY_10);

        // when & then
        assertThatThrownBy(() -> requestRefund(membershipId, newKey()))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(PaymentErrorCode.MEMBERSHIP_REFUND_SETTLEMENT_BLOCKED);
        assertThat(attemptOpsReason(attemptId)).isEqualTo(MembershipOpsReviewReason.REFUND_TARGET_PERIOD_MISMATCH.name());
        assertThat(refundsOfMember()).isEmpty();
        assertThat(PORTONE.totalCancelCount()).isZero();
        assertThat(PORTONE.totalGetCount()).isZero();
        assertThat(membership(membershipId).getTerminatedAt()).isNull();
    }

    @Test
    @DisplayName("탈퇴한 회원의 새 즉시 해지 요청은 거절된다(MEMBER-001)")
    void withdrawnMemberNewRequestIsRejected() {
        // given
        Long membershipId = renewedMembership();
        withdraw();
        clock.fixAt(DAY_10);

        // when & then
        assertThatThrownBy(() -> requestRefund(membershipId, newKey()))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
        assertThat(refundsOfMember()).isEmpty();
    }

    @Test
    @DisplayName("키가 없으면 COMMON-007, 65자 이상·공백이 있으면 COMMON-001이고 접수되지 않는다")
    void invalidKeysAreRejectedBeforeAccept() {
        // given
        Long membershipId = renewedMembership();
        clock.fixAt(DAY_10);

        // when & then
        assertThatThrownBy(() -> requestRefund(membershipId, " "))
            .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.MISSING_IDEMPOTENCY_KEY);
        assertThatThrownBy(() -> requestRefund(membershipId, "a".repeat(65)))
            .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> requestRefund(membershipId, "has space"))
            .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
        assertThat(refundsOfMember()).isEmpty();
    }
}
