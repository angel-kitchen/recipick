package com.recipick.domain.payment.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.domain.payment.dto.response.PaymentCancelResponse;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.service.PaymentCancelOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentCancelPreparationTest {

    private static final PaymentCancelResult FINISHED = new PaymentCancelResult(
        PaymentCancelOutcome.CANCELED_BEFORE_PAY,
        new PaymentCancelResponse(10L, 1L, PaymentStatus.FAILED, PaymentFailureReason.CANCELED_BEFORE_PAY,
            null, null, 0L));

    @Test
    @DisplayName("끝난 결과만 있으면 PortOne 호출이 필요 없다")
    void finished() {
        // when
        PaymentCancelPreparation preparation = PaymentCancelPreparation.finished(FINISHED);

        // then
        assertThat(preparation.requiresPortOneCall()).isFalse();
        assertThat(preparation.finishedResult()).isSameAs(FINISHED);
        assertThat(preparation.refundId()).isNull();
    }

    @Test
    @DisplayName("호출 인자만 있으면 PortOne 호출이 필요하다")
    void callPortOne() {
        // when
        PaymentCancelPreparation preparation = PaymentCancelPreparation.callPortOne(55L, "pay_1", 23800L);

        // then
        assertThat(preparation.requiresPortOneCall()).isTrue();
        assertThat(preparation.finishedResult()).isNull();
        assertThat(preparation.refundId()).isEqualTo(55L);
        assertThat(preparation.portonePaymentId()).isEqualTo("pay_1");
        assertThat(preparation.amount()).isEqualTo(23800L);
    }

    @Test
    @DisplayName("끝난 결과와 호출 인자가 둘 다 있으면 만들 수 없다 — 호출 인자가 일부만 섞여도 마찬가지다")
    void rejectsBoth() {
        // when
        Throwable both = catchThrowable(() -> new PaymentCancelPreparation(FINISHED, 55L, "pay_1", 23800L));
        Throwable partial = catchThrowable(() -> new PaymentCancelPreparation(FINISHED, 55L, null, null));

        // then
        assertThat(both).isInstanceOf(IllegalArgumentException.class);
        assertThat(partial).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("끝난 결과도 호출 인자도 없거나 호출 인자가 일부만 있으면 만들 수 없다")
    void rejectsNeither() {
        // when
        Throwable none = catchThrowable(() -> new PaymentCancelPreparation(null, null, null, null));
        Throwable partial = catchThrowable(() -> new PaymentCancelPreparation(null, 55L, null, 23800L));

        // then
        assertThat(none).isInstanceOf(IllegalArgumentException.class);
        assertThat(partial).isInstanceOf(IllegalArgumentException.class);
    }
}
