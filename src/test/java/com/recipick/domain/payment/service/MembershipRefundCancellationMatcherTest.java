package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.service.MembershipRefundCancellationMatcher.CancelledSum;
import com.recipick.domain.payment.service.MembershipRefundCancellationMatcher.Identity;
import com.recipick.domain.payment.service.MembershipRefundCancellationMatcher.Kind;
import com.recipick.domain.payment.service.MembershipRefundCancellationMatcher.Match;
import com.recipick.infra.portone.dto.PortOneCancellation;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneCancellationTrigger;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 식별 검증·누적 취소액·취소 소유권 매칭의 순수 규칙. 금액이 같다는 이유로 연결하지 않고, 일시적 불일치와 명백한 모순을 구분한다
class MembershipRefundCancellationMatcherTest {

    private static final String PAYMENT_ID = "mpay_a";
    private static final String STORE = "store-1";
    private static final String REASON = "RECIPICK_MEMBERSHIP_REFUND_V1_10_mrf_v1_x";
    private static final LocalDateTime AT = LocalDateTime.of(2027, 3, 10, 15, 0);

    private static final RefundMatchSnapshot TARGET = new RefundMatchSnapshot(10L, PAYMENT_ID, STORE, 3_900L, 1_862L,
        REASON, null, MembershipRefundStatus.REQUESTED);

    // ---------- 식별 ----------

    @Test
    @DisplayName("결제 ID·상점·KRW·원수납액이 모두 같으면 MATCH, 원수납액은 환불액이 아니라 수납액과 비교한다")
    void identityMatchesOnStoreCurrencyAndPaidAmount() {
        // given
        PortOnePayment payment = payment(PortOnePaymentStatus.PAID, STORE, 3_900L, "KRW", 0L, List.of());

        // when & then
        assertThat(MembershipRefundCancellationMatcher.identity(TARGET, payment)).isEqualTo(Identity.MATCH);
        assertThat(MembershipRefundCancellationMatcher.identity(TARGET,
            payment(PortOnePaymentStatus.PAID, STORE, 1_862L, "KRW", 0L, List.of()))).isEqualTo(Identity.MISMATCH);
    }

    @Test
    @DisplayName("상점이 다르거나 KRW가 아니거나 결제 ID가 다르면 MISMATCH, 필수 필드가 없거나 진행 중 상태면 INCOMPLETE, PG FAILED는 PAYMENT_FAILED")
    void identityDistinguishesMismatchFromIncomplete() {
        // given & when & then
        assertThat(MembershipRefundCancellationMatcher.identity(TARGET,
            payment(PortOnePaymentStatus.PAID, "other", 3_900L, "KRW", 0L, List.of()))).isEqualTo(Identity.MISMATCH);
        assertThat(MembershipRefundCancellationMatcher.identity(TARGET,
            payment(PortOnePaymentStatus.PAID, STORE, 3_900L, "USD", 0L, List.of()))).isEqualTo(Identity.MISMATCH);
        assertThat(MembershipRefundCancellationMatcher.identity(TARGET,
            new PortOnePayment("mpay_b", "tx", PortOnePaymentStatus.PAID, 3_900L, "KRW", AT, null, null, List.of(),
                STORE, 0L))).isEqualTo(Identity.MISMATCH);
        assertThat(MembershipRefundCancellationMatcher.identity(TARGET,
            payment(PortOnePaymentStatus.PAID, null, 3_900L, "KRW", 0L, List.of()))).isEqualTo(Identity.INCOMPLETE);
        assertThat(MembershipRefundCancellationMatcher.identity(TARGET,
            payment(PortOnePaymentStatus.PAID, STORE, null, "KRW", 0L, List.of()))).isEqualTo(Identity.INCOMPLETE);
        assertThat(MembershipRefundCancellationMatcher.identity(TARGET,
            payment(PortOnePaymentStatus.PENDING, STORE, 3_900L, "KRW", 0L, List.of()))).isEqualTo(Identity.INCOMPLETE);
        assertThat(MembershipRefundCancellationMatcher.identity(TARGET,
            payment(PortOnePaymentStatus.FAILED, STORE, 3_900L, "KRW", 0L, List.of()))).isEqualTo(Identity.PAYMENT_FAILED);
        assertThat(MembershipRefundCancellationMatcher.identity(TARGET,
            payment(PortOnePaymentStatus.PARTIAL_CANCELED, STORE, 3_900L, "KRW", 1_862L, List.of())))
            .isEqualTo(Identity.MATCH);
    }

    // ---------- 누적 취소액 ----------

    @Test
    @DisplayName("누적 취소액은 SUCCEEDED 취소의 totalAmount 합과 비교한다 — FAILED·REQUESTED만 있으면 0원이 맞다")
    void cancelledSumCountsOnlySucceeded() {
        // given
        List<PortOneCancellation> failedAndRequested = List.of(
            cancellation("cxl_f", PortOneCancellationStatus.FAILED, 3_900L, REASON, PortOneCancellationTrigger.API),
            cancellation("cxl_r", PortOneCancellationStatus.REQUESTED, 3_900L, REASON, PortOneCancellationTrigger.API));

        // when & then
        assertThat(MembershipRefundCancellationMatcher.cancelledSum(
            payment(PortOnePaymentStatus.PAID, STORE, 3_900L, "KRW", 0L, failedAndRequested)))
            .isEqualTo(CancelledSum.CONSISTENT);
        assertThat(MembershipRefundCancellationMatcher.cancelledSum(
            payment(PortOnePaymentStatus.PAID, STORE, 3_900L, "KRW", 3_900L, failedAndRequested)))
            .isEqualTo(CancelledSum.INCONSISTENT);
        assertThat(MembershipRefundCancellationMatcher.cancelledSum(
            payment(PortOnePaymentStatus.PARTIAL_CANCELED, STORE, 3_900L, "KRW", 1_862L, List.of(
                cancellation("cxl_s", PortOneCancellationStatus.SUCCEEDED, 1_862L, REASON,
                    PortOneCancellationTrigger.API))))).isEqualTo(CancelledSum.CONSISTENT);
    }

    @Test
    @DisplayName("배열 없음·누적 0은 정상이고, 배열은 있는데 누적 취소액이 없으면 일시적 불일치, 알 수 없는 상태·중복 ID·금액 누락은 불신뢰다")
    void cancelledSumHandlesOptionalFieldsAndUntrustedEntries() {
        // given
        PortOneCancellation ok = cancellation("cxl_1", PortOneCancellationStatus.SUCCEEDED, 1_000L, REASON,
            PortOneCancellationTrigger.API);

        // when & then
        assertThat(MembershipRefundCancellationMatcher.cancelledSum(
            payment(PortOnePaymentStatus.PAID, STORE, 3_900L, "KRW", null, List.of()))).isEqualTo(CancelledSum.CONSISTENT);
        assertThat(MembershipRefundCancellationMatcher.cancelledSum(
            payment(PortOnePaymentStatus.PAID, STORE, 3_900L, "KRW", null, List.of(ok)))).isEqualTo(CancelledSum.INCONSISTENT);
        assertThat(MembershipRefundCancellationMatcher.cancelledSum(
            payment(PortOnePaymentStatus.PAID, STORE, 3_900L, "KRW", 1_000L, List.of(ok,
                cancellation("cxl_u", PortOneCancellationStatus.UNKNOWN, 0L, null, PortOneCancellationTrigger.UNKNOWN)))))
            .isEqualTo(CancelledSum.UNTRUSTED);
        assertThat(MembershipRefundCancellationMatcher.cancelledSum(
            payment(PortOnePaymentStatus.PAID, STORE, 3_900L, "KRW", 2_000L, List.of(ok, ok))))
            .isEqualTo(CancelledSum.UNTRUSTED);
        assertThat(MembershipRefundCancellationMatcher.cancelledSum(
            payment(PortOnePaymentStatus.PAID, STORE, 3_900L, "KRW", 1_000L, List.of(
                cancellation("cxl_n", PortOneCancellationStatus.SUCCEEDED, null, REASON, PortOneCancellationTrigger.API)))))
            .isEqualTo(CancelledSum.UNTRUSTED);
    }

    // ---------- 소유권 매칭 ----------

    @Test
    @DisplayName("저장된 취소 ID가 있으면 ID로만 매칭한다(EXACT_ID). 보이지 않으면 NONE이고, 같은 reason의 다른 ID가 있으면 AMBIGUOUS다")
    void storedIdMatchesExactly() {
        // given
        RefundMatchSnapshot withId = new RefundMatchSnapshot(10L, PAYMENT_ID, STORE, 3_900L, 1_862L, REASON, "cxl_1",
            MembershipRefundStatus.REQUESTED);
        PortOneCancellation own = cancellation("cxl_1", PortOneCancellationStatus.SUCCEEDED, 1_862L, REASON,
            PortOneCancellationTrigger.API);
        PortOneCancellation foreign = cancellation("cxl_x", PortOneCancellationStatus.SUCCEEDED, 1_862L, "manual",
            PortOneCancellationTrigger.CONSOLE);
        PortOneCancellation sameReasonOtherId = cancellation("cxl_2", PortOneCancellationStatus.SUCCEEDED, 1_862L, REASON,
            PortOneCancellationTrigger.API);

        // when
        Match visible = MembershipRefundCancellationMatcher.match(withId,
            payment(PortOnePaymentStatus.PARTIAL_CANCELED, STORE, 3_900L, "KRW", 1_862L, List.of(own)), bindings(withId));
        Match notVisible = MembershipRefundCancellationMatcher.match(withId,
            payment(PortOnePaymentStatus.PARTIAL_CANCELED, STORE, 3_900L, "KRW", 1_862L, List.of(foreign)),
            bindings(withId));
        Match duplicated = MembershipRefundCancellationMatcher.match(withId,
            payment(PortOnePaymentStatus.CANCELED, STORE, 3_900L, "KRW", 3_724L, List.of(own, sameReasonOtherId)),
            bindings(withId));

        // then
        assertThat(visible.kind()).isEqualTo(Kind.EXACT_ID);
        assertThat(visible.cancellation()).isSameAs(own);
        assertThat(visible.unexplainedActivity()).isFalse();
        assertThat(notVisible.kind()).isEqualTo(Kind.NONE);
        assertThat(notVisible.unexplainedActivity()).isTrue();
        assertThat(duplicated.kind()).isEqualTo(Kind.AMBIGUOUS);
    }

    @Test
    @DisplayName("저장된 ID가 없으면 서버 생성 reason이 정확히 같은 API 취소 하나만 BY_REASON으로 연결한다")
    void matchesByReasonOnlyForApiCancellationWithExactReason() {
        // given
        PortOneCancellation own = cancellation("cxl_1", PortOneCancellationStatus.REQUESTED, 1_862L, REASON,
            PortOneCancellationTrigger.API);
        PortOneCancellation consoleSameReason = cancellation("cxl_c", PortOneCancellationStatus.SUCCEEDED, 1_862L, REASON,
            PortOneCancellationTrigger.CONSOLE);

        // when
        Match api = MembershipRefundCancellationMatcher.match(TARGET,
            payment(PortOnePaymentStatus.PAID, STORE, 3_900L, "KRW", 0L, List.of(own)), bindings(TARGET));
        Match console = MembershipRefundCancellationMatcher.match(TARGET,
            payment(PortOnePaymentStatus.PARTIAL_CANCELED, STORE, 3_900L, "KRW", 1_862L, List.of(consoleSameReason)),
            bindings(TARGET));

        // then
        assertThat(api.kind()).isEqualTo(Kind.BY_REASON);
        assertThat(api.cancellation()).isSameAs(own);
        assertThat(console.kind()).isEqualTo(Kind.NONE);
        assertThat(console.unexplainedActivity()).isTrue();
    }

    @Test
    @DisplayName("같은 금액이지만 다른 reason의 취소는 연결하지 않고(NONE), 설명되지 않는 성공 취소로 표시한다")
    void sameAmountDifferentReasonIsNotMatched() {
        // given
        PortOneCancellation foreign = cancellation("cxl_x", PortOneCancellationStatus.SUCCEEDED, 1_862L,
            "RECIPICK_MEMBERSHIP_REFUND_V1_99_mrf_v1_other", PortOneCancellationTrigger.API);

        // when
        Match match = MembershipRefundCancellationMatcher.match(TARGET,
            payment(PortOnePaymentStatus.PARTIAL_CANCELED, STORE, 3_900L, "KRW", 1_862L, List.of(foreign)),
            bindings(TARGET));

        // then
        assertThat(match.kind()).isEqualTo(Kind.NONE);
        assertThat(match.cancellation()).isNull();
        assertThat(match.unexplainedActivity()).isTrue();
    }

    @Test
    @DisplayName("같은 reason의 서로 다른 취소 ID가 여러 개면 AMBIGUOUS다")
    void duplicateReasonCandidatesAreAmbiguous() {
        // given
        List<PortOneCancellation> two = List.of(
            cancellation("cxl_1", PortOneCancellationStatus.SUCCEEDED, 1_862L, REASON, PortOneCancellationTrigger.API),
            cancellation("cxl_2", PortOneCancellationStatus.REQUESTED, 1_862L, REASON, PortOneCancellationTrigger.API));

        // when
        Match match = MembershipRefundCancellationMatcher.match(TARGET,
            payment(PortOnePaymentStatus.PARTIAL_CANCELED, STORE, 3_900L, "KRW", 1_862L, two), bindings(TARGET));

        // then
        assertThat(match.kind()).isEqualTo(Kind.AMBIGUOUS);
    }

    @Test
    @DisplayName("매칭된 취소가 다른 환불에 귀속돼 있으면 FOREIGN_LINKED, 다른 FAILED 환불의 취소가 성공으로 보이면 CONTRADICTION")
    void foreignLinkedAndContradiction() {
        // given
        RefundCancellationBinding other = new RefundCancellationBinding(9L, MembershipRefundStatus.COMPLETED, "cxl_1",
            "RECIPICK_MEMBERSHIP_REFUND_V1_9_mrf_v1_o");
        PortOneCancellation shared = cancellation("cxl_1", PortOneCancellationStatus.SUCCEEDED, 1_862L, REASON,
            PortOneCancellationTrigger.API);
        RefundCancellationBinding failedOther = new RefundCancellationBinding(9L, MembershipRefundStatus.FAILED, "cxl_9",
            "RECIPICK_MEMBERSHIP_REFUND_V1_9_mrf_v1_o");
        PortOneCancellation contradicting = cancellation("cxl_9", PortOneCancellationStatus.SUCCEEDED, 3_900L,
            "RECIPICK_MEMBERSHIP_REFUND_V1_9_mrf_v1_o", PortOneCancellationTrigger.API);

        // when
        Match foreign = MembershipRefundCancellationMatcher.match(TARGET,
            payment(PortOnePaymentStatus.PARTIAL_CANCELED, STORE, 3_900L, "KRW", 1_862L, List.of(shared)),
            List.of(bindings(TARGET).get(0), other));
        Match contradiction = MembershipRefundCancellationMatcher.match(TARGET,
            payment(PortOnePaymentStatus.CANCELED, STORE, 3_900L, "KRW", 3_900L, List.of(contradicting)),
            List.of(bindings(TARGET).get(0), failedOther));

        // then
        assertThat(foreign.kind()).isEqualTo(Kind.FOREIGN_LINKED);
        assertThat(contradiction.kind()).isEqualTo(Kind.CONTRADICTION);
        assertThat(contradiction.cancellation()).isSameAs(contradicting);
    }

    @Test
    @DisplayName("다른 환불의 ID나 reason으로 설명되는 취소는 설명되지 않는 활동이 아니고, FAILED 취소는 활동으로 세지 않는다")
    void explainedByOtherBindingIsNotUnexplained() {
        // given
        RefundCancellationBinding other = new RefundCancellationBinding(9L, MembershipRefundStatus.REQUESTED, null,
            "RECIPICK_MEMBERSHIP_REFUND_V1_9_mrf_v1_o");
        List<PortOneCancellation> cancellations = List.of(
            cancellation("cxl_9", PortOneCancellationStatus.REQUESTED, 3_900L,
                "RECIPICK_MEMBERSHIP_REFUND_V1_9_mrf_v1_o", PortOneCancellationTrigger.API),
            cancellation("cxl_f", PortOneCancellationStatus.FAILED, 1_000L, "manual", PortOneCancellationTrigger.CONSOLE));

        // when
        Match match = MembershipRefundCancellationMatcher.match(TARGET,
            payment(PortOnePaymentStatus.PAID, STORE, 3_900L, "KRW", 0L, cancellations),
            List.of(bindings(TARGET).get(0), other));

        // then
        assertThat(match.kind()).isEqualTo(Kind.NONE);
        assertThat(match.unexplainedActivity()).isFalse();
    }

    private static List<RefundCancellationBinding> bindings(RefundMatchSnapshot target) {
        return List.of(new RefundCancellationBinding(target.refundId(), target.status(), target.portoneCancellationId(),
            target.pgRequestReason()));
    }

    private static PortOnePayment payment(PortOnePaymentStatus status, String storeId, Long total, String currency,
                                          Long cancelled, List<PortOneCancellation> cancellations) {
        return new PortOnePayment(PAYMENT_ID, "tx", status, total, currency, AT, null, null, cancellations, storeId,
            cancelled);
    }

    private static PortOneCancellation cancellation(String id, PortOneCancellationStatus status, Long totalAmount,
                                                    String reason, PortOneCancellationTrigger trigger) {
        return new PortOneCancellation(id, status, totalAmount, AT, status == PortOneCancellationStatus.SUCCEEDED ? AT : null,
            trigger, reason);
    }
}
