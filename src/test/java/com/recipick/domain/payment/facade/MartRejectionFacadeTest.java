package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.payment.dto.AdminRefundExecution;
import com.recipick.domain.payment.dto.MartRejectionPaymentResult;
import com.recipick.domain.payment.dto.MartRejectionPreparation;
import com.recipick.domain.payment.dto.response.MartRejectionResponse;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.MartRejectionOutcome;
import com.recipick.domain.payment.service.RefundReconcileOutcome;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MartRejectionFacadeTest {

    private static final Long ORDER_ID = 1L;
    private static final Long MART_ID = 3L;
    private static final Long ADMIN_ID = 17L;
    private static final Long PAYMENT_ID = 10L;
    private static final Long REFUND_ID = 77L;
    private static final Long AMOUNT = 23800L;
    private static final String KEY = "admin-key-1";
    private static final String REASON = "상품 준비가 어렵습니다.";
    private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 10, 28, 14, 0, 40);
    private static final MartRejectionPaymentResult PENDING_RESULT = new MartRejectionPaymentResult(
        MartRejectionOutcome.PENDING, response(PaymentStatus.CANCEL_REQUESTED, null, RefundStatus.REQUESTED));
    private static final MartRejectionPaymentResult COMPLETED_RESULT = new MartRejectionPaymentResult(
        MartRejectionOutcome.COMPLETED, response(PaymentStatus.CANCELED, CANCELED_AT, RefundStatus.COMPLETED));
    private static final MartRejectionPaymentResult PG_FAILED_RESULT = new MartRejectionPaymentResult(
        MartRejectionOutcome.PG_FAILED, response(PaymentStatus.PAID, null, RefundStatus.FAILED));

    @Mock
    private MartRejectionProcessor martRejectionProcessor;

    @Mock
    private AdminRefundExecutionFacade adminRefundExecutionFacade;

    @InjectMocks
    private MartRejectionFacade facade;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Idempotency-Key가 비어 있으면 COMMON-007이고 접수·실행 모두 하지 않는다")
    void rejectBlankKey(String key) {
        // when
        Throwable thrown = catchThrowable(() -> reject(key));

        // then
        assertErrorCode(thrown, CommonErrorCode.MISSING_IDEMPOTENCY_KEY);
        verifyNoInteractions(martRejectionProcessor, adminRefundExecutionFacade);
    }

    @Test
    @DisplayName("Idempotency-Key가 64자를 넘으면 COMMON-001이고 접수·실행 모두 하지 않는다")
    void rejectTooLongKey() {
        // when
        Throwable thrown = catchThrowable(() -> reject("k".repeat(65)));

        // then
        assertErrorCode(thrown, CommonErrorCode.INVALID_INPUT);
        verifyNoInteractions(martRejectionProcessor, adminRefundExecutionFacade);
    }

    @Test
    @DisplayName("새 거절이면 접수 커밋 → 안전망 등록 → 실행 순으로 부르고 실행기가 반영한 COMPLETED를 돌려준다")
    void executeAfterPrepare() {
        // given
        givenPrepared(MartRejectionPreparation.execute(PENDING_RESULT));
        given(adminRefundExecutionFacade.execute(REFUND_ID))
            .willReturn(AdminRefundExecution.applied(RefundReconcileOutcome.RESOLVED, COMPLETED_RESULT));

        // when
        MartRejectionPaymentResult result = reject(KEY);

        // then
        assertThat(result).isEqualTo(COMPLETED_RESULT);
        // 실행이 서버 중단으로 끝나지 못해도 Worker가 이어받도록 실행보다 먼저 등록한다
        InOrder order = inOrder(martRejectionProcessor, adminRefundExecutionFacade);
        order.verify(martRejectionProcessor).prepare(AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID, KEY, REASON);
        order.verify(adminRefundExecutionFacade).scheduleSafetyNet(REFUND_ID);
        order.verify(adminRefundExecutionFacade).execute(REFUND_ID);
        verify(martRejectionProcessor, never()).getCurrentResult(anyLong(), anyLong());
    }

    @Test
    @DisplayName("실행기가 반영한 결과가 PENDING이면 예외 없이 그대로 돌려준다")
    void returnAppliedPending() {
        // given
        givenPrepared(MartRejectionPreparation.execute(PENDING_RESULT));
        given(adminRefundExecutionFacade.execute(REFUND_ID))
            .willReturn(AdminRefundExecution.applied(RefundReconcileOutcome.RESCHEDULED, PENDING_RESULT));

        // when
        MartRejectionPaymentResult result = reject(KEY);

        // then
        assertThat(result).isEqualTo(PENDING_RESULT);
        verify(martRejectionProcessor, never()).getCurrentResult(anyLong(), anyLong());
    }

    @ParameterizedTest
    @CsvSource({
        "PG_FAILED, PAYMENT-009",
        "REQUEST_REJECTED, COMMON-008"
    })
    @DisplayName("실행기가 반영한 결과가 실패면 TX2 커밋 뒤 Facade가 실패 종류의 오류 코드로 예외를 던진다")
    void throwAppliedFailure(MartRejectionOutcome outcome, String expectedCode) {
        // given
        MartRejectionPaymentResult failed =
            new MartRejectionPaymentResult(outcome, response(PaymentStatus.PAID, null, RefundStatus.FAILED));
        givenPrepared(MartRejectionPreparation.execute(PENDING_RESULT));
        given(adminRefundExecutionFacade.execute(REFUND_ID))
            .willReturn(AdminRefundExecution.applied(RefundReconcileOutcome.RESOLVED, failed));

        // when
        Throwable thrown = catchThrowable(() -> reject(KEY));

        // then
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode().getCode()).isEqualTo(expectedCode));
    }

    @Test
    @DisplayName("같은 키의 환불이 이미 완료됐으면 안전망 등록·실행 없이 COMPLETED를 돌려준다")
    void returnFinishedCompleted() {
        // given
        givenPrepared(MartRejectionPreparation.finished(COMPLETED_RESULT));

        // when
        MartRejectionPaymentResult result = reject(KEY);

        // then
        assertThat(result).isEqualTo(COMPLETED_RESULT);
        verifyNoInteractions(adminRefundExecutionFacade);
    }

    @Test
    @DisplayName("같은 키의 환불이 이미 실패했으면 안전망 등록·실행 없이 원래 실패 코드로 예외를 던진다")
    void throwFinishedFailure() {
        // given
        givenPrepared(MartRejectionPreparation.finished(PG_FAILED_RESULT));

        // when
        Throwable thrown = catchThrowable(() -> reject(KEY));

        // then
        assertErrorCode(thrown, PaymentErrorCode.PAYMENT_CANCEL_REJECTED);
        verifyNoInteractions(adminRefundExecutionFacade);
    }

    @ParameterizedTest
    @EnumSource(value = RefundReconcileOutcome.class, names = {"SKIPPED", "RESCHEDULED", "STALLED"})
    @DisplayName("실행기가 반영하지 않았으면 현재 상태를 다시 읽어 그 결과를 돌려준다")
    void returnCurrentResultWhenNotApplied(RefundReconcileOutcome outcome) {
        // given
        givenPrepared(MartRejectionPreparation.execute(PENDING_RESULT));
        given(adminRefundExecutionFacade.execute(REFUND_ID)).willReturn(AdminRefundExecution.notApplied(outcome));
        given(martRejectionProcessor.getCurrentResult(PAYMENT_ID, REFUND_ID)).willReturn(COMPLETED_RESULT);

        // when
        MartRejectionPaymentResult result = reject(KEY);

        // then
        assertThat(result).isEqualTo(COMPLETED_RESULT);
    }

    @Test
    @DisplayName("웹훅이 먼저 실패로 끝내 실행기가 건너뛰었으면 다시 읽은 실패 코드로 예외를 던진다")
    void throwCurrentFailureWhenSkipped() {
        // given
        givenPrepared(MartRejectionPreparation.execute(PENDING_RESULT));
        given(adminRefundExecutionFacade.execute(REFUND_ID))
            .willReturn(AdminRefundExecution.notApplied(RefundReconcileOutcome.SKIPPED));
        given(martRejectionProcessor.getCurrentResult(PAYMENT_ID, REFUND_ID)).willReturn(PG_FAILED_RESULT);

        // when
        Throwable thrown = catchThrowable(() -> reject(KEY));

        // then
        assertErrorCode(thrown, PaymentErrorCode.PAYMENT_CANCEL_REJECTED);
    }

    @Test
    @DisplayName("수동 확인이면 현재 환불이 FAILED여도 실패로 확정하지 않고 다시 읽은 본문에 PENDING으로 답한다")
    void returnPendingOnManualReview() {
        // given
        givenPrepared(MartRejectionPreparation.execute(PENDING_RESULT));
        given(adminRefundExecutionFacade.execute(REFUND_ID))
            .willReturn(AdminRefundExecution.notApplied(RefundReconcileOutcome.MANUAL_REVIEW));
        given(martRejectionProcessor.getCurrentResult(PAYMENT_ID, REFUND_ID)).willReturn(PG_FAILED_RESULT);

        // when
        MartRejectionPaymentResult result = reject(KEY);

        // then
        // PG 취소가 성공했을 수 있는 경합이라 409로 답하면 안 된다
        assertThat(result.outcome()).isEqualTo(MartRejectionOutcome.PENDING);
        assertThat(result.response()).isEqualTo(PG_FAILED_RESULT.response());
    }

    private MartRejectionPaymentResult reject(String key) {
        return facade.reject(AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID, key, REASON);
    }

    private void givenPrepared(MartRejectionPreparation preparation) {
        given(martRejectionProcessor.prepare(AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID, KEY, REASON))
            .willReturn(preparation);
    }

    private static MartRejectionResponse response(PaymentStatus paymentStatus, LocalDateTime canceledAt,
                                                  RefundStatus refundStatus) {
        return new MartRejectionResponse(ORDER_ID, PAYMENT_ID, paymentStatus, canceledAt, REFUND_ID, refundStatus,
            AMOUNT);
    }

    private static void assertErrorCode(Throwable thrown, ErrorCode expected) {
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
