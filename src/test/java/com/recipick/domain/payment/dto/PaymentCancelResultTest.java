package com.recipick.domain.payment.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.domain.payment.dto.response.PaymentCancelResponse;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.service.PaymentCancelOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentCancelResultTest {

    private static final PaymentCancelResponse RESPONSE = new PaymentCancelResponse(
        10L, 1L, PaymentStatus.FAILED, PaymentFailureReason.CANCELED_BEFORE_PAY, null, null, 0L);

    @Test
    @DisplayName("outcome과 응답이 모두 있어야 만들 수 있다")
    void requiresBoth() {
        // when
        Throwable noOutcome = catchThrowable(() -> new PaymentCancelResult(null, RESPONSE));
        Throwable noResponse = catchThrowable(() -> new PaymentCancelResult(PaymentCancelOutcome.CANCELED, null));

        // then — Facade의 result.outcome().errorCode()가 내부 실수로 NPE 나는 길을 미리 막는다
        assertThat(noOutcome).isInstanceOf(NullPointerException.class);
        assertThat(noResponse).isInstanceOf(NullPointerException.class);
        assertThat(new PaymentCancelResult(PaymentCancelOutcome.CANCELED_BEFORE_PAY, RESPONSE).response())
            .isSameAs(RESPONSE);
    }
}
