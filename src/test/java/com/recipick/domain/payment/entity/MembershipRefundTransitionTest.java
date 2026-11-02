package com.recipick.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipRefundOpsReason;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.service.MembershipRefundCalculator.Calculation;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// 환불 행의 생성·실행권·고정 요청값·확정·운영 표시 전이. 저장된 행(ID 있음)이 전제인 부분은 ReflectionTestUtils로 ID만 채운다
class MembershipRefundTransitionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2027, 3, 10, 15, 0);
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2027, 2, 28, 10, 3);
    private static final LocalDateTime START = PAID_AT;
    private static final LocalDateTime END = LocalDateTime.of(2027, 3, 31, 10, 3);
    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final Calculation CALC = new Calculation(31, 10, 1_258L, 780L, 1_862L);

    @Test
    @DisplayName("MEMBER 환불은 계산 결과·기간·원수납액(회차 금액)·요청 해시·PG 키(mrf_v1_)를 접수 때 저장하고 최초 발송 시각은 비어 있다")
    void requestedByMemberStoresCalculationAndPgKey() {
        // given
        MembershipPaymentAttempt attempt = appliedPaidAttempt(42L, 7L);

        // when
        MembershipRefund refund = MembershipRefund.requestedByMember(attempt.getMembershipPayment(), attempt, CALC,
            "mrf_member:7:k1", "hash", START, END, NOW);

        // then
        assertThat(refund.getStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(refund.getRequestedBy()).isEqualTo(RefundRequester.MEMBER);
        assertThat(refund.getAmount()).isEqualTo(1_862L);
        assertThat(refund.getUsedDays()).isEqualTo(10);
        assertThat(refund.getUsedDayFee()).isEqualTo(1_258L);
        assertThat(refund.getPenaltyAmount()).isEqualTo(780L);
        assertThat(refund.getPeriodDays()).isEqualTo(31);
        assertThat(refund.getPaidAmountSnapshot()).isEqualTo(3_900L);
        assertThat(refund.getPeriodStartAt()).isEqualTo(START);
        assertThat(refund.getPeriodEndAt()).isEqualTo(END);
        assertThat(refund.getRequestHash()).isEqualTo("hash");
        assertThat(refund.getIdempotencyKey()).isEqualTo("mrf_member:7:k1");
        assertThat(refund.getReason()).isEqualTo(MembershipRefund.REASON_MEMBER_TERMINATION);
        assertThat(refund.getPgIdempotencyKey()).startsWith(MembershipRefund.PG_KEY_PREFIX).hasSize(43);
        assertThat(refund.getFirstDispatchedAt()).isNull();
        assertThat(refund.getPgStoreId()).isNull();
        assertThat(refund.getClaimToken()).isZero();
    }

    @Test
    @DisplayName("환불 필요 표시가 있거나 PAID가 아닌 시도로는 MEMBER 환불을 만들 수 없다")
    void requestedByMemberRequiresAppliedPaidAttempt() {
        // given
        MembershipPaymentAttempt requiringRefund = appliedPaidAttempt(1L, 7L);
        requiringRefund.requireRefund(NOW, MembershipOpsReviewReason.PAID_CONFLICT);

        // when & then
        assertThatThrownBy(() -> MembershipRefund.requestedByMember(requiringRefund.getMembershipPayment(),
            requiringRefund, CALC, "k", "h", START, END, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("SYSTEM 환불 요청은 기존 키 규칙(mrf_sys_{attemptId}_{n})을 유지하면서 원수납액 스냅숏과 PG 키를 함께 저장한다")
    void systemRefundRequestedKeepsSignatureAndStoresPgKey() {
        // given
        MembershipPaymentAttempt attempt = appliedPaidAttempt(42L, 7L);
        attempt.requireRefund(NOW, MembershipOpsReviewReason.PAID_AMOUNT_MISMATCH);

        // when
        MembershipRefund refund = MembershipRefund.systemRefundRequested(attempt, 3_800L, "PAID_AMOUNT_MISMATCH", 1, NOW);

        // then
        assertThat(refund.getIdempotencyKey()).isEqualTo("mrf_sys_42_1");
        assertThat(refund.getPaidAmountSnapshot()).isEqualTo(3_800L);
        assertThat(refund.getPgIdempotencyKey()).startsWith(MembershipRefund.PG_KEY_PREFIX);
        assertThat(refund.getFirstDispatchedAt()).isNull();
        assertThat(refund.getPeriodDays()).isNull();
    }

    @Test
    @DisplayName("SYSTEM FAILED 행의 후속은 금액·사유·시도를 그대로 쓰고 retryOfRefundId·새 PG 키를 받는다. FAILED SYSTEM이 아니면 만들 수 없다")
    void systemRetryOfCopiesAmountAndLinksToFailed() {
        // given
        MembershipPaymentAttempt attempt = appliedPaidAttempt(42L, 7L);
        attempt.requireRefund(NOW, MembershipOpsReviewReason.PAID_CONFLICT);
        MembershipRefund failed = MembershipRefund.systemRefundRequested(attempt, 3_900L, "PAID_CONFLICT", 1, NOW);
        ReflectionTestUtils.setField(failed, "id", 900L);
        failed.recordCancellationId("cxl_1");
        failed.markFailed("PG_CANCEL_FAILED", "cxl_1");

        // when
        MembershipRefund retry = MembershipRefund.systemRetryOf(failed, 2, NOW.plusHours(1));

        // then
        assertThat(retry.getStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(retry.getAmount()).isEqualTo(3_900L);
        assertThat(retry.getReason()).isEqualTo("PAID_CONFLICT");
        assertThat(retry.getIdempotencyKey()).isEqualTo("mrf_sys_42_2");
        assertThat(retry.getRetryOfRefundId()).isEqualTo(900L);
        assertThat(retry.getPgIdempotencyKey()).isNotEqualTo(failed.getPgIdempotencyKey());
        assertThat(retry.getPortoneCancellationId()).isNull();
        assertThat(failed.getStatus()).isEqualTo(MembershipRefundStatus.FAILED);

        MembershipRefund requested = MembershipRefund.systemRefundRequested(attempt, 3_900L, "PAID_CONFLICT", 3, NOW);
        ReflectionTestUtils.setField(requested, "id", 901L);
        assertThatThrownBy(() -> MembershipRefund.systemRetryOf(requested, 4, NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("실행권은 REQUESTED·운영 표시 없음·임대 없음(또는 만료)일 때만 토큰을 올리고, 상태는 바꾸지 않는다")
    void claimRaisesTokenOnlyWhenFree() {
        // given
        MembershipRefund refund = memberRefund(10L);

        // when
        boolean first = refund.claim(NOW, LEASE);
        boolean whileLeased = refund.claim(NOW.plusMinutes(4), LEASE);
        boolean afterLease = refund.claim(NOW.plusMinutes(5), LEASE);

        // then
        assertThat(first).isTrue();
        assertThat(whileLeased).isFalse();
        assertThat(afterLease).isTrue();
        assertThat(refund.getClaimToken()).isEqualTo(2L);
        assertThat(refund.getLeaseExpiresAt()).isEqualTo(NOW.plusMinutes(10));
        assertThat(refund.getStatus()).isEqualTo(MembershipRefundStatus.REQUESTED);
        assertThat(refund.holdsClaim(2L, NOW.plusMinutes(9))).isTrue();
        assertThat(refund.holdsClaim(1L, NOW.plusMinutes(9))).isFalse();
        assertThat(refund.holdsClaim(2L, NOW.plusMinutes(10))).isFalse();

        refund.requireOpsReview(MembershipRefundOpsReason.PG_AUTH_ERROR, NOW);
        assertThat(refund.claim(NOW.plusMinutes(20), LEASE)).isFalse();
    }

    @Test
    @DisplayName("고정 요청값은 비어 있을 때만 채우고 다시 불러도 덮어쓰지 않으며, 최초 발송 시각은 한 번만 기록한다")
    void fixedRequestValuesAreWrittenOnce() {
        // given
        MembershipRefund refund = memberRefund(10L);

        // when
        refund.fixDispatchRequest("store-a");
        refund.fixDispatchRequest("store-b");
        refund.markFirstDispatched(NOW);
        refund.markFirstDispatched(NOW.plusMinutes(3));

        // then
        assertThat(refund.getPgStoreId()).isEqualTo("store-a");
        assertThat(refund.getPgRequestReason())
            .isEqualTo(MembershipRefund.PG_REASON_PREFIX + "10_" + refund.getPgIdempotencyKey());
        assertThat(refund.hasFixedDispatchRequest()).isTrue();
        assertThat(refund.getFirstDispatchedAt()).isEqualTo(NOW);
        assertThat(refund.checkBaseAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> refund.fixDispatchRequest(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("취소 ID는 처음 기록되고, 같은 ID는 true·다른 ID는 false다")
    void recordCancellationIdKeepsFirst() {
        // given
        MembershipRefund refund = memberRefund(10L);

        // when & then
        assertThat(refund.recordCancellationId("cxl_1")).isTrue();
        assertThat(refund.recordCancellationId("cxl_1")).isTrue();
        assertThat(refund.recordCancellationId("cxl_2")).isFalse();
        assertThat(refund.getPortoneCancellationId()).isEqualTo("cxl_1");
    }

    @Test
    @DisplayName("완료는 REQUESTED에서만, 취소 ID 없는 완료는 MEMBER 0원만 허용하고 임대·예약을 끝낸다")
    void markCompletedRequiresCancellationIdExceptZeroMember() {
        // given
        MembershipRefund positive = memberRefund(10L);
        positive.claim(NOW, LEASE);
        positive.scheduleNextCheck(NOW.plusMinutes(1));
        MembershipRefund zero = memberRefund(11L, new Calculation(30, 24, 3_120L, 780L, 0L));

        // when
        assertThatThrownBy(() -> positive.markCompleted(NOW, NOW, null)).isInstanceOf(IllegalArgumentException.class);
        positive.markCompleted(NOW.plusMinutes(2), NOW.plusMinutes(1), "cxl_1");
        zero.markCompleted(NOW, null, null);

        // then
        assertThat(positive.getStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(positive.getCompletedAt()).isEqualTo(NOW.plusMinutes(2));
        assertThat(positive.getPgCancelledAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(positive.getPortoneCancellationId()).isEqualTo("cxl_1");
        assertThat(positive.getLeaseExpiresAt()).isNull();
        assertThat(positive.getNextCheckAt()).isNull();
        assertThat(zero.getStatus()).isEqualTo(MembershipRefundStatus.COMPLETED);
        assertThat(zero.getPortoneCancellationId()).isNull();
        assertThatThrownBy(() -> positive.markCompleted(NOW, NOW, "cxl_1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("실패 확정은 취소 ID가 맞는 FAILED 취소에서만 하고, 저장된 ID와 다른 ID로는 하지 않는다")
    void markFailedRequiresMatchingCancellationId() {
        // given
        MembershipRefund refund = memberRefund(10L);
        refund.recordCancellationId("cxl_1");

        // when & then
        assertThatThrownBy(() -> refund.markFailed("PG_CANCEL_FAILED", "cxl_2")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> refund.markFailed("declined by pg", "cxl_1")).isInstanceOf(IllegalArgumentException.class);
        refund.markFailed("PG_CANCEL_FAILED", "cxl_1");
        assertThat(refund.getStatus()).isEqualTo(MembershipRefundStatus.FAILED);
        assertThat(refund.getFailureReason()).isEqualTo("PG_CANCEL_FAILED");
        assertThat(refund.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("운영 표시: 자동 중단 사유끼리는 처음을 유지하고 강한 차단은 덮어쓰며, 확정된 행의 과거 표시는 reviewRequired false다")
    void opsReviewPrecedenceAndReviewRequired() {
        // given
        MembershipRefund refund = memberRefund(10L);

        // when
        refund.requireOpsReview(MembershipRefundOpsReason.UNKNOWN_WINDOW_EXPIRED, NOW);
        refund.requireOpsReview(MembershipRefundOpsReason.PG_AUTH_ERROR, NOW.plusMinutes(1));
        boolean haltedBeforeBlock = refund.isAutoHalted();
        refund.requireOpsReview(MembershipRefundOpsReason.EVIDENCE_CONFLICT, NOW.plusMinutes(2));
        refund.requireOpsReview(MembershipRefundOpsReason.TARGET_PERIOD_MISMATCH, NOW.plusMinutes(3));

        // then
        assertThat(haltedBeforeBlock).isTrue();
        assertThat(refund.getOpsReviewReason()).isEqualTo(MembershipRefundOpsReason.EVIDENCE_CONFLICT);
        assertThat(refund.getOpsReviewRequiredAt()).isEqualTo(NOW.plusMinutes(2));
        assertThat(refund.isSettlementBlocked()).isTrue();
        assertThat(refund.isReviewRequired()).isTrue();

        MembershipRefund halted = memberRefund(11L);
        halted.requireOpsReview(MembershipRefundOpsReason.UNKNOWN_WINDOW_EXPIRED, NOW);
        assertThat(halted.isReviewRequired()).isTrue();
        halted.markCompleted(NOW.plusHours(1), NOW.plusHours(1), "cxl_9");
        assertThat(halted.isReviewRequired()).isFalse();
        assertThat(halted.getOpsReviewReason()).isEqualTo(MembershipRefundOpsReason.UNKNOWN_WINDOW_EXPIRED);
    }

    private static MembershipRefund memberRefund(Long id) {
        return memberRefund(id, CALC);
    }

    private static MembershipRefund memberRefund(Long id, Calculation calculation) {
        MembershipPaymentAttempt attempt = appliedPaidAttempt(42L, 7L);
        MembershipRefund refund = MembershipRefund.requestedByMember(attempt.getMembershipPayment(), attempt, calculation,
            "mrf_member:7:" + id, "hash", START, END, NOW);
        ReflectionTestUtils.setField(refund, "id", id);
        return refund;
    }

    // 정상 반영된 PAID 시도(환불 필요 표시 없음). 회차는 PAID·반영 시각 있음
    private static MembershipPaymentAttempt appliedPaidAttempt(Long attemptId, Long memberId) {
        MembershipPayment payment = MembershipPayment.pendingFirst(10L, null, null, 3_900L,
            LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 30));
        ReflectionTestUtils.setField(payment, "id", 500L);
        BillingKey key = BillingKey.builder().memberId(memberId).portoneBillingKey("billing-key-test-0001")
            .status(BillingKeyStatus.ACTIVE).issuedAt(NOW).build();
        MembershipPaymentAttempt attempt = MembershipPaymentAttempt.ready(payment, 1, key, PAID_AT.minusMinutes(5),
            Duration.ofMinutes(10));
        ReflectionTestUtils.setField(attempt, "id", attemptId);
        attempt.claim(PAID_AT.minusMinutes(1));
        attempt.markPaid(PAID_AT, PAID_AT.plusMinutes(1));
        payment.markPaid(PAID_AT, PAID_AT.plusMinutes(1));
        return attempt;
    }
}
