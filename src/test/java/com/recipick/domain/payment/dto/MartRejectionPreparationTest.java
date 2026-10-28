package com.recipick.domain.payment.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.domain.payment.dto.response.MartRejectionResponse;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.service.MartRejectionOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MartRejectionPreparationTest {

    private static final MartRejectionResponse RESPONSE = new MartRejectionResponse(
        1L, 10L, PaymentStatus.CANCEL_REQUESTED, null, 55L, RefundStatus.REQUESTED, 23800L);

    @Test
    @DisplayName("PENDING 결과는 실행이 필요하고 실행 대상은 응답의 환불 ID다")
    void execute() {
        // given
        MartRejectionPaymentResult pending = new MartRejectionPaymentResult(MartRejectionOutcome.PENDING, RESPONSE);

        // when
        MartRejectionPreparation preparation = MartRejectionPreparation.execute(pending);

        // then
        assertThat(preparation.requiresExecution()).isTrue();
        assertThat(preparation.result()).isSameAs(pending);
        assertThat(preparation.refundId()).isEqualTo(55L);
    }

    @ParameterizedTest
    @EnumSource(value = MartRejectionOutcome.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("끝난 결과는 실행이 필요 없다")
    void finished(MartRejectionOutcome outcome) {
        // given
        MartRejectionPaymentResult finished = new MartRejectionPaymentResult(outcome, RESPONSE);

        // when
        MartRejectionPreparation preparation = MartRejectionPreparation.finished(finished);

        // then
        assertThat(preparation.requiresExecution()).isFalse();
        assertThat(preparation.result()).isSameAs(finished);
    }

    @ParameterizedTest
    @EnumSource(value = MartRejectionOutcome.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("끝난 결과로는 실행을 요청할 수 없다 — 이미 확정된 환불에 PortOne을 다시 부르지 않기 위해")
    void rejectsExecuteWithFinishedOutcome(MartRejectionOutcome outcome) {
        // when
        Throwable thrown = catchThrowable(() ->
            MartRejectionPreparation.execute(new MartRejectionPaymentResult(outcome, RESPONSE)));

        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("PENDING 결과는 끝난 것으로 만들 수 없다 — 실행 없이 응답하면 환불이 Fallback까지 멈춘다")
    void rejectsFinishedWithPending() {
        // when
        Throwable thrown = catchThrowable(() ->
            MartRejectionPreparation.finished(new MartRejectionPaymentResult(MartRejectionOutcome.PENDING, RESPONSE)));

        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("결과 없이는 만들 수 없다")
    void requiresResult() {
        // when
        Throwable thrown = catchThrowable(() -> new MartRejectionPreparation(null, false));

        // then
        assertThat(thrown).isInstanceOf(NullPointerException.class);
    }
}
