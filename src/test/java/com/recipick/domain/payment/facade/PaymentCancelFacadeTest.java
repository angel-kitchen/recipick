package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.payment.dto.PaymentCancelPreparation;
import com.recipick.domain.payment.dto.PaymentCancelResult;
import com.recipick.domain.payment.dto.response.PaymentCancelResponse;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.PaymentCancelOutcome;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.global.exception.ErrorCode;
import com.recipick.infra.portone.PortOnePaymentClient;
import com.recipick.infra.portone.dto.PortOneCancelOutcome;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentCancelFacadeTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long PAYMENT_ID = 10L;
    private static final Long ORDER_ID = 1L;
    private static final Long REFUND_ID = 55L;
    private static final String KEY = "cancel-key-1";
    private static final String REASON = "단순 변심";
    private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 10, 23, 19, 8, 40);
    private static final PortOneCancelResult PORTONE_SUCCEEDED =
        new PortOneCancelResult(PortOneCancelOutcome.SUCCEEDED, "cancel_1", CANCELED_AT, "cancellation SUCCEEDED");
    private static final PaymentCancelResult BEFORE_PAY_RESULT = new PaymentCancelResult(
        PaymentCancelOutcome.CANCELED_BEFORE_PAY,
        new PaymentCancelResponse(PAYMENT_ID, ORDER_ID, PaymentStatus.FAILED,
            PaymentFailureReason.CANCELED_BEFORE_PAY, null, null, 0L));
    private static final PaymentCancelResult CANCELED_RESULT = new PaymentCancelResult(
        PaymentCancelOutcome.CANCELED,
        new PaymentCancelResponse(PAYMENT_ID, ORDER_ID, PaymentStatus.CANCELED, null, CANCELED_AT,
            RefundStatus.COMPLETED, 23800L));

    @Mock
    private PaymentCancelProcessor paymentCancelProcessor;

    @Mock
    private PortOnePaymentClient portOnePaymentClient;

    @InjectMocks
    private PaymentCancelFacade facade;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("Idempotency-Key가 null·빈 값·공백이면 MISSING_IDEMPOTENCY_KEY고 아무것도 부르지 않는다")
    void blankIdempotencyKey(String idempotencyKey) {
        // when
        Throwable thrown = catchThrowable(() -> facade.cancel(MEMBER_ID, PAYMENT_ID, idempotencyKey, REASON));

        // then
        assertErrorCode(thrown, CommonErrorCode.MISSING_IDEMPOTENCY_KEY);
        verifyNoInteractions(paymentCancelProcessor, portOnePaymentClient);
    }

    @Test
    @DisplayName("Idempotency-Key가 64자를 넘으면 INVALID_INPUT이고 아무것도 부르지 않는다")
    void tooLongIdempotencyKey() {
        // when
        Throwable thrown = catchThrowable(() -> facade.cancel(MEMBER_ID, PAYMENT_ID, "k".repeat(65), REASON));

        // then
        assertErrorCode(thrown, CommonErrorCode.INVALID_INPUT);
        verifyNoInteractions(paymentCancelProcessor, portOnePaymentClient);
    }

    @Test
    @DisplayName("TX1에서 끝났으면 PortOne·TX2 없이 TX1이 만든 결과를 그대로 돌려준다")
    void finishedInPrepare() {
        // given — 64자는 허용 경계다
        String key = "k".repeat(64);
        given(paymentCancelProcessor.prepare(MEMBER_ID, PAYMENT_ID, key, REASON))
            .willReturn(PaymentCancelPreparation.finished(BEFORE_PAY_RESULT));

        // when
        PaymentCancelResult result = facade.cancel(MEMBER_ID, PAYMENT_ID, key, REASON);

        // then — Facade가 DB를 다시 읽어 만들지 않는다. 커밋 뒤 읽으면 다른 요청이 바꾼 값이 섞일 수 있다
        assertThat(result).isSameAs(BEFORE_PAY_RESULT);
        verifyNoInteractions(portOnePaymentClient);
        verify(paymentCancelProcessor, never()).apply(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("PortOne 호출이 필요하면 TX1 → PortOne(서버 키 recipick-refund-{refundId}) → TX2 순으로 부르고 TX2 결과를 돌려준다")
    void callsPortOneBetweenTransactions() {
        // given
        given(paymentCancelProcessor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON))
            .willReturn(PaymentCancelPreparation.callPortOne(REFUND_ID, "pay_1", 23800L));
        given(portOnePaymentClient.cancelPayment("pay_1", "recipick-refund-55", REASON, 23800L))
            .willReturn(PORTONE_SUCCEEDED);
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, PORTONE_SUCCEEDED)).willReturn(CANCELED_RESULT);

        // when
        PaymentCancelResult result = facade.cancel(MEMBER_ID, PAYMENT_ID, KEY, REASON);

        // then — 클라이언트 키는 PortOne에 가지 않는다
        assertThat(result).isSameAs(CANCELED_RESULT);
        InOrder inOrder = inOrder(paymentCancelProcessor, portOnePaymentClient);
        inOrder.verify(paymentCancelProcessor).prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON);
        inOrder.verify(portOnePaymentClient).cancelPayment("pay_1", "recipick-refund-55", REASON, 23800L);
        inOrder.verify(paymentCancelProcessor).apply(PAYMENT_ID, REFUND_ID, PORTONE_SUCCEEDED);
    }

    @ParameterizedTest
    @CsvSource({
        "PG_FAILED, PAYMENT_CANCEL_REJECTED",
        "REQUEST_REJECTED, EXTERNAL_SERVICE_ERROR"
    })
    @DisplayName("TX2가 실패 outcome을 돌려주면 TX2가 끝난 뒤에 그 오류 코드로 예외를 던진다")
    void throwsAfterApply(PaymentCancelOutcome outcome, String expectedCodeName) {
        // given
        PaymentCancelResult failed = new PaymentCancelResult(outcome,
            new PaymentCancelResponse(PAYMENT_ID, ORDER_ID, PaymentStatus.PAID, null, null,
                RefundStatus.FAILED, 23800L));
        given(paymentCancelProcessor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON))
            .willReturn(PaymentCancelPreparation.callPortOne(REFUND_ID, "pay_1", 23800L));
        given(portOnePaymentClient.cancelPayment("pay_1", "recipick-refund-55", REASON, 23800L))
            .willReturn(PORTONE_SUCCEEDED);
        given(paymentCancelProcessor.apply(PAYMENT_ID, REFUND_ID, PORTONE_SUCCEEDED)).willReturn(failed);

        // when
        Throwable thrown = catchThrowable(() -> facade.cancel(MEMBER_ID, PAYMENT_ID, KEY, REASON));

        // then — apply가 먼저 끝났으므로(=TX2 커밋) PAID 복귀·REFUND FAILED는 남아 있다
        assertErrorCode(thrown, errorCodeNamed(expectedCodeName));
        verify(paymentCancelProcessor).apply(PAYMENT_ID, REFUND_ID, PORTONE_SUCCEEDED);
    }

    @Test
    @DisplayName("TX1이 같은 키의 FAILED 환불을 재현하면 PortOne·TX2 없이 바로 그 오류를 던진다")
    void throwsForReplayedFailure() {
        // given
        PaymentCancelResult replayed = new PaymentCancelResult(PaymentCancelOutcome.PG_FAILED,
            new PaymentCancelResponse(PAYMENT_ID, ORDER_ID, PaymentStatus.PAID, null, null,
                RefundStatus.FAILED, 23800L));
        given(paymentCancelProcessor.prepare(MEMBER_ID, PAYMENT_ID, KEY, REASON))
            .willReturn(PaymentCancelPreparation.finished(replayed));

        // when
        Throwable thrown = catchThrowable(() -> facade.cancel(MEMBER_ID, PAYMENT_ID, KEY, REASON));

        // then
        assertErrorCode(thrown, PaymentErrorCode.PAYMENT_CANCEL_REJECTED);
        verifyNoInteractions(portOnePaymentClient);
        verify(paymentCancelProcessor, never()).apply(anyLong(), anyLong(), any());
    }

    // CsvSource는 enum 하나로만 바꿔 주므로 두 enum에 걸친 오류 코드는 이름으로 찾는다
    private static ErrorCode errorCodeNamed(String name) {
        return switch (name) {
            case "PAYMENT_CANCEL_REJECTED" -> PaymentErrorCode.PAYMENT_CANCEL_REJECTED;
            case "EXTERNAL_SERVICE_ERROR" -> CommonErrorCode.EXTERNAL_SERVICE_ERROR;
            default -> throw new IllegalArgumentException(name);
        };
    }

    private static void assertErrorCode(Throwable thrown, ErrorCode expected) {
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
