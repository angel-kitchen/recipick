package com.recipick.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundFailureType;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.global.exception.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RefundTest {

    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 10, 23, 19, 8, 30);
    private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 10, 23, 19, 8, 40);
    private static final String CANCELLATION_ID = "cancel_1";

    @Test
    @DisplayName("SYSTEM 환불은 REQUESTED 상태로 생성되고 넘긴 멱등키가 저장된다")
    void requestedBySystem() {
        // given
        Payment payment = Payment.builder()
            .orderId(1L)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(PaymentStatus.FAILED)
            .expiresAt(LocalDateTime.of(2026, 10, 20, 17, 0))
            .build();

        LocalDateTime requestedAt = LocalDateTime.of(2026, 10, 20, 17, 10);


        // when
        Refund refund = Refund.requestedBySystem(
            payment,
            23800L,
            "결제 금액 불일치",
            "key_1",
            requestedAt
        );

        // then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getRequestedBy()).isEqualTo(RefundRequester.SYSTEM);
        assertThat(refund.getAmount()).isEqualTo(23800L);
        assertThat(refund.getReason()).isEqualTo("결제 금액 불일치");
        assertThat(refund.getIdempotencyKey()).isEqualTo("key_1");
        assertThat(refund.getRequestedAt()).isEqualTo(requestedAt);
        assertThat(refund.getPayment()).isSameAs(payment);

    }

    @Test
    @DisplayName("MEMBER 환불은 REQUESTED 상태로 생성되고 클라이언트 멱등키가 그대로 저장된다")
    void requestedByMember() {
        // given
        Payment payment = cancelRequestedPayment();

        // when
        Refund refund = Refund.requestedByMember(payment, "단순 변심", "client-key-1", REQUESTED_AT);

        // then — 회원 취소는 전액 환불만 있으므로 금액은 결제 금액과 같다
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getRequestedBy()).isEqualTo(RefundRequester.MEMBER);
        assertThat(refund.getAmount()).isEqualTo(payment.getAmount());
        assertThat(refund.getReason()).isEqualTo("단순 변심");
        assertThat(refund.getIdempotencyKey()).isEqualTo("client-key-1");
        assertThat(refund.getRequestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(refund.getPayment()).isSameAs(payment);
        // PortOne에 아직 묻지 않았으니 PG 쪽 정보는 비어 있다
        assertThat(refund.getPortoneCancellationId()).isNull();
        assertThat(refund.getCompletedAt()).isNull();
        assertThat(refund.getFailureType()).isNull();
        assertThat(refund.getFailureReason()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = "CANCEL_REQUESTED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("취소 요청 상태가 아닌 결제에는 MEMBER 환불을 만들 수 없다")
    void requestedByMemberWhenPaymentNotCancelRequested(PaymentStatus status) {
        // given — 회원 환불은 Payment.requestCancel() 뒤에만 생긴다
        Payment payment = paymentWith(status);

        // when
        Throwable thrown = catchThrowable(() ->
            Refund.requestedByMember(payment, "단순 변심", "client-key-1", REQUESTED_AT));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS));
    }

    @Test
    @DisplayName("PortOne이 처리 중이라고 답하면 상태는 REQUESTED로 두고 취소 내역 ID만 남긴다")
    void recordRequested() {
        // given
        Refund refund = memberRefund();

        // when
        refund.recordRequested(CANCELLATION_ID);

        // then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(refund.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("같은 취소 내역 ID를 다시 기록해도 그대로다 — 같은 서버 키 재호출은 같은 ID를 돌려준다")
    void recordRequestedAgainWithSameId() {
        // given
        Refund refund = memberRefund();
        refund.recordRequested(CANCELLATION_ID);

        // when
        refund.recordRequested(CANCELLATION_ID);

        // then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
    }

    @Test
    @DisplayName("처리 중으로 기록된 환불은 같은 취소 내역 ID로 COMPLETED가 된다")
    void completeAfterRecordRequested() {
        // given
        Refund refund = memberRefund();
        refund.recordRequested(CANCELLATION_ID);

        // when
        refund.complete(CANCELLATION_ID, COMPLETED_AT);

        // then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(refund.getCompletedAt()).isEqualTo(COMPLETED_AT);
    }

    @Test
    @DisplayName("처리 중으로 기록된 환불은 같은 취소 내역 ID로 FAILED가 된다 — 웹훅·재조회가 늦게 확인한 실패")
    void failAfterRecordRequested() {
        // given
        Refund refund = memberRefund();
        refund.recordRequested(CANCELLATION_ID);

        // when
        refund.fail(CANCELLATION_ID, RefundFailureType.PG_FAILED, "cancellation FAILED");

        // then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(refund.getFailureType()).isEqualTo(RefundFailureType.PG_FAILED);
        assertThat(refund.getFailureReason()).isEqualTo("cancellation FAILED");
    }

    @Test
    @DisplayName("이미 기록된 취소 내역 ID와 다른 ID로는 COMPLETED로 변경할 수 없다")
    void completeWithDifferentCancellationId() {
        // given — 환불 행 하나는 PortOne 취소 작업 하나다. ID가 바뀌면 다른 취소를 우리 환불로 잘못 묶은 것이다
        Refund refund = memberRefund();
        refund.recordRequested(CANCELLATION_ID);

        // when
        Throwable thrown = catchThrowable(() -> refund.complete("cancel_2", COMPLETED_AT));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(refund.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("이미 기록된 취소 내역 ID와 다른 ID로는 FAILED로 변경할 수 없다")
    void failWithDifferentCancellationId() {
        // given
        Refund refund = memberRefund();
        refund.recordRequested(CANCELLATION_ID);

        // when
        Throwable thrown = catchThrowable(() ->
            refund.fail("cancel_2", RefundFailureType.PG_FAILED, "cancellation FAILED"));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(refund.getFailureType()).isNull();
        assertThat(refund.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("이미 취소 내역 ID가 기록된 환불은 ID 없이 FAILED로 변경할 수 없다")
    void failWithoutCancellationIdWhenAlreadyRecorded() {
        // given — ID가 생긴 환불은 PortOne을 다시 부르지 않으므로 ID 없는 거절 응답이 올 수 없다
        Refund refund = memberRefund();
        refund.recordRequested(CANCELLATION_ID);

        // when
        Throwable thrown = catchThrowable(() ->
            refund.fail(null, RefundFailureType.REQUEST_REJECTED, "HTTP 401 UNAUTHORIZED"));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(refund.getFailureType()).isNull();
        assertThat(refund.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("취소 내역 ID 없이는 처리 중으로 기록할 수 없다")
    void recordRequestedWithoutCancellationId() {
        // given
        Refund refund = memberRefund();

        // when
        Throwable thrown = catchThrowable(() -> refund.recordRequested(null));

        // then
        assertThat(thrown).isInstanceOf(NullPointerException.class);
        assertThat(refund.getPortoneCancellationId()).isNull();
    }

    @Test
    @DisplayName("REQUESTED 환불을 취소 내역 ID와 PG가 확인한 완료 시각으로 COMPLETED로 변경한다")
    void complete() {
        // given
        Refund refund = memberRefund();

        // when
        refund.complete(CANCELLATION_ID, COMPLETED_AT);

        // then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(refund.getCompletedAt()).isEqualTo(COMPLETED_AT);
        assertThat(refund.getFailureType()).isNull();
        assertThat(refund.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("완료 시각 없이는 COMPLETED로 변경할 수 없다 — 서버 시각으로 대신 채우지 않는다")
    void completeWithoutCompletedAt() {
        // given
        Refund refund = memberRefund();

        // when
        Throwable thrown = catchThrowable(() -> refund.complete(CANCELLATION_ID, null));

        // then
        assertThat(thrown).isInstanceOf(NullPointerException.class);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
    }

    @Test
    @DisplayName("취소 내역 ID 없이는 COMPLETED로 변경할 수 없다")
    void completeWithoutCancellationId() {
        // given
        Refund refund = memberRefund();

        // when
        Throwable thrown = catchThrowable(() -> refund.complete(null, COMPLETED_AT));

        // then
        assertThat(thrown).isInstanceOf(NullPointerException.class);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
    }

    @Test
    @DisplayName("PortOne이 FAILED를 명시하면 취소 내역 ID·실패 종류 PG_FAILED·사유와 함께 FAILED로 변경한다")
    void fail() {
        // given
        Refund refund = memberRefund();

        // when
        refund.fail(CANCELLATION_ID, RefundFailureType.PG_FAILED, "cancellation FAILED");

        // then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
        assertThat(refund.getFailureType()).isEqualTo(RefundFailureType.PG_FAILED);
        assertThat(refund.getFailureReason()).isEqualTo("cancellation FAILED");
        assertThat(refund.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("PG가 처리 전에 거절한 경우는 취소 내역 ID 없이 실패 종류 REQUEST_REJECTED로 FAILED가 된다")
    void failWithoutCancellationId() {
        // given — 400·401·403 오류 응답에는 cancellation 객체가 없다
        Refund refund = memberRefund();

        // when
        refund.fail(null, RefundFailureType.REQUEST_REJECTED, "HTTP 401 UNAUTHORIZED");

        // then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getPortoneCancellationId()).isNull();
        assertThat(refund.getFailureType()).isEqualTo(RefundFailureType.REQUEST_REJECTED);
        assertThat(refund.getFailureReason()).isEqualTo("HTTP 401 UNAUTHORIZED");
    }

    @Test
    @DisplayName("PortOne이 FAILED를 명시한 경우는 취소 내역 ID 없이 FAILED로 변경할 수 없다")
    void failPgFailedWithoutCancellationId() {
        // given — FAILED 응답에는 cancellation 객체가 있으므로 ID가 없다는 건 결과를 잘못 옮긴 것이다
        Refund refund = memberRefund();

        // when
        Throwable thrown = catchThrowable(() ->
            refund.fail(null, RefundFailureType.PG_FAILED, "cancellation FAILED"));

        // then
        assertThat(thrown).isInstanceOf(NullPointerException.class);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
    }

    @Test
    @DisplayName("PG가 처리 전에 거절한 실패에는 취소 내역 ID를 함께 기록할 수 없다")
    void failRequestRejectedWithCancellationId() {
        // given — 거절 응답에는 cancellation 객체가 없다. ID가 있다는 건 결과를 잘못 옮긴 것이다
        Refund refund = memberRefund();

        // when
        Throwable thrown = catchThrowable(() ->
            refund.fail(CANCELLATION_ID, RefundFailureType.REQUEST_REJECTED, "HTTP 401 UNAUTHORIZED"));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getPortoneCancellationId()).isNull();
        assertThat(refund.getFailureType()).isNull();
        assertThat(refund.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("실패 종류 없이는 FAILED로 변경할 수 없다 — 재요청이 원래 응답을 재현할 수 없게 된다")
    void failWithoutFailureType() {
        // given
        Refund refund = memberRefund();

        // when
        Throwable thrown = catchThrowable(() -> refund.fail(CANCELLATION_ID, null, "cancellation FAILED"));

        // then
        assertThat(thrown).isInstanceOf(NullPointerException.class);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
    }

    @Test
    @DisplayName("실패 사유 없이는 FAILED로 변경할 수 없다")
    void failWithoutFailureReason() {
        // given
        Refund refund = memberRefund();

        // when
        Throwable thrown = catchThrowable(() -> refund.fail(CANCELLATION_ID, RefundFailureType.PG_FAILED, null));

        // then
        assertThat(thrown).isInstanceOf(NullPointerException.class);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getFailureType()).isNull();
        assertThat(refund.getFailureReason()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = RefundStatus.class, names = "REQUESTED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("이미 끝난 환불은 처리 중으로 기록할 수 없다")
    void recordRequestedWhenFinished(RefundStatus status) {
        // given
        Refund refund = memberRefundIn(status);

        // when
        Throwable thrown = catchThrowable(() -> refund.recordRequested("cancel_2"));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS));
        assertThat(refund.getStatus()).isEqualTo(status);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
    }

    @ParameterizedTest
    @EnumSource(value = RefundStatus.class, names = "REQUESTED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("이미 끝난 환불은 다시 COMPLETED로 변경할 수 없다")
    void completeWhenFinished(RefundStatus status) {
        // given — 완료·실패 기록이 덮어써지면 어느 결과가 진짜였는지 알 수 없다
        Refund refund = memberRefundIn(status);

        // when
        Throwable thrown = catchThrowable(() -> refund.complete("cancel_2", COMPLETED_AT));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS));
        assertThat(refund.getStatus()).isEqualTo(status);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
    }

    @ParameterizedTest
    @EnumSource(value = RefundStatus.class, names = "REQUESTED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("이미 끝난 환불은 다시 FAILED로 변경할 수 없다")
    void failWhenFinished(RefundStatus status) {
        // given
        Refund refund = memberRefundIn(status);

        // when
        Throwable thrown = catchThrowable(() ->
            refund.fail("cancel_2", RefundFailureType.PG_FAILED, "late failure"));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS));
        assertThat(refund.getStatus()).isEqualTo(status);
        assertThat(refund.getPortoneCancellationId()).isEqualTo(CANCELLATION_ID);
    }

    private Payment cancelRequestedPayment() {
        return paymentWith(PaymentStatus.CANCEL_REQUESTED);
    }

    private Payment paymentWith(PaymentStatus status) {
        return Payment.builder()
            .orderId(1L)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(status)
            .expiresAt(LocalDateTime.of(2026, 10, 20, 17, 0))
            .build();
    }

    private Refund memberRefund() {
        return Refund.requestedByMember(cancelRequestedPayment(), "단순 변심", "client-key-1", REQUESTED_AT);
    }

    // 빌더는 REQUESTED만 만들 수 있으므로 끝난 환불은 실제 전이 메서드로 만든다
    private Refund memberRefundIn(RefundStatus status) {
        Refund refund = memberRefund();
        switch (status) {
            case COMPLETED -> refund.complete(CANCELLATION_ID, COMPLETED_AT);
            case FAILED -> refund.fail(CANCELLATION_ID, RefundFailureType.PG_FAILED, "cancellation FAILED");
            case REQUESTED -> refund.recordRequested(CANCELLATION_ID);
        }
        return refund;
    }
}
