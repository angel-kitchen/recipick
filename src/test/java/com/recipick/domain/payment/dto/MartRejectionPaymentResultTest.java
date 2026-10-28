package com.recipick.domain.payment.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.domain.payment.dto.response.MartRejectionResponse;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.MartRejectionOutcome;
import com.recipick.global.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MartRejectionPaymentResultTest {

    private static final MartRejectionResponse RESPONSE = new MartRejectionResponse(
        1L, 10L, PaymentStatus.CANCEL_REQUESTED, null, 55L, RefundStatus.REQUESTED, 23800L);

    @Test
    @DisplayName("outcome과 응답이 모두 있어야 만들 수 있다")
    void requiresBoth() {
        // when
        Throwable noOutcome = catchThrowable(() -> new MartRejectionPaymentResult(null, RESPONSE));
        Throwable noResponse = catchThrowable(() -> new MartRejectionPaymentResult(MartRejectionOutcome.PENDING, null));

        // then — Facade의 result.outcome().errorCode()가 내부 실수로 NPE 나는 길을 미리 막는다
        assertThat(noOutcome).isInstanceOf(NullPointerException.class);
        assertThat(noResponse).isInstanceOf(NullPointerException.class);
        assertThat(new MartRejectionPaymentResult(MartRejectionOutcome.PENDING, RESPONSE).response())
            .isSameAs(RESPONSE);
    }

    @Test
    @DisplayName("성공·대기는 오류가 없고, PG 실패는 PAYMENT-009, 요청 거절은 COMMON-008이다")
    void errorCodes() {
        // then — 회원 취소와 같은 오류로 답해 관리자 화면이 같은 분기를 쓴다
        assertThat(MartRejectionOutcome.COMPLETED.errorCode()).isEmpty();
        assertThat(MartRejectionOutcome.PENDING.errorCode()).isEmpty();
        assertThat(MartRejectionOutcome.PG_FAILED.errorCode()).contains(PaymentErrorCode.PAYMENT_CANCEL_REJECTED);
        assertThat(MartRejectionOutcome.REQUEST_REJECTED.errorCode()).contains(CommonErrorCode.EXTERNAL_SERVICE_ERROR);
    }
}
