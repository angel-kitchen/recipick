package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.payment.dto.response.PaymentCancelResponse;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentCancelFacadeTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long PAYMENT_ID = 10L;
    private static final String REASON = "단순 변심";

    @Mock
    private PaymentCancelProcessor paymentCancelProcessor;

    @InjectMocks
    private PaymentCancelFacade facade;

    @Test
    @DisplayName("Idempotency-Key가 있으면 키는 넘기지 않고 Processor에 회원·결제·사유만 위임한다")
    void delegatesToProcessor() {
        // given — 결제 전 취소는 키를 저장하지 않으므로 Processor는 키를 받을 필요가 없다
        PaymentCancelResponse expected = new PaymentCancelResponse(PAYMENT_ID, 1L, PaymentStatus.FAILED,
            PaymentFailureReason.CANCELED_BEFORE_PAY, null, null, 0L);
        given(paymentCancelProcessor.cancelBeforePay(MEMBER_ID, PAYMENT_ID, REASON)).willReturn(expected);

        // when
        PaymentCancelResponse response = facade.cancel(MEMBER_ID, PAYMENT_ID, "cancel-key-1", REASON);

        // then
        assertThat(response).isSameAs(expected);
        verify(paymentCancelProcessor).cancelBeforePay(MEMBER_ID, PAYMENT_ID, REASON);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("Idempotency-Key가 null·빈 값·공백이면 MISSING_IDEMPOTENCY_KEY고 Processor는 부르지 않는다")
    void blankIdempotencyKey(String idempotencyKey) {
        // when
        Throwable thrown = catchThrowable(() -> facade.cancel(MEMBER_ID, PAYMENT_ID, idempotencyKey, REASON));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.MISSING_IDEMPOTENCY_KEY));
        verifyNoInteractions(paymentCancelProcessor);
    }
}
