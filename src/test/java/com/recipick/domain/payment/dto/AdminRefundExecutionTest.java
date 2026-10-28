package com.recipick.domain.payment.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.domain.payment.dto.response.MartRejectionResponse;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.service.MartRejectionOutcome;
import com.recipick.domain.payment.service.RefundReconcileOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminRefundExecutionTest {

    private static final MartRejectionPaymentResult COMPLETED = new MartRejectionPaymentResult(
        MartRejectionOutcome.COMPLETED,
        new MartRejectionResponse(1L, 10L, PaymentStatus.CANCELED, null, 55L, RefundStatus.COMPLETED, 23800L));

    @Test
    @DisplayName("반영 결과가 있으면 그대로 꺼낼 수 있다")
    void applied() {
        // when
        AdminRefundExecution execution = AdminRefundExecution.applied(RefundReconcileOutcome.RESOLVED, COMPLETED);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.RESOLVED);
        assertThat(execution.appliedResult()).contains(COMPLETED);
    }

    @Test
    @DisplayName("TX2까지 가지 않았으면 반영 결과가 비어 있다 — 거절 API는 TX1 시점의 결과로 답한다")
    void notApplied() {
        // when
        AdminRefundExecution execution = AdminRefundExecution.notApplied(RefundReconcileOutcome.RESCHEDULED);

        // then
        assertThat(execution.outcome()).isEqualTo(RefundReconcileOutcome.RESCHEDULED);
        assertThat(execution.appliedResult()).isEmpty();
    }

    @Test
    @DisplayName("RESOLVED인데 반영 결과가 없거나 SKIPPED인데 반영 결과가 있으면 만들 수 없다")
    void rejectsInconsistentOutcome() {
        // when
        Throwable resolvedWithoutResult = catchThrowable(() ->
            AdminRefundExecution.notApplied(RefundReconcileOutcome.RESOLVED));
        Throwable skippedWithResult = catchThrowable(() ->
            AdminRefundExecution.applied(RefundReconcileOutcome.SKIPPED, COMPLETED));

        // then
        assertThat(resolvedWithoutResult).isInstanceOf(IllegalArgumentException.class);
        assertThat(skippedWithResult).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("MANUAL_REVIEW는 반영 결과를 가질 수 없다 — PG 성공과 어긋난 TX2 결과가 거절 API 응답으로 나가지 않게 한다")
    void rejectsManualReviewWithResult() {
        // when
        Throwable manualReviewWithResult = catchThrowable(() ->
            AdminRefundExecution.applied(RefundReconcileOutcome.MANUAL_REVIEW, COMPLETED));

        // then
        assertThat(manualReviewWithResult).isInstanceOf(IllegalArgumentException.class);
        assertThat(AdminRefundExecution.notApplied(RefundReconcileOutcome.MANUAL_REVIEW).appliedResult()).isEmpty();
    }
}
