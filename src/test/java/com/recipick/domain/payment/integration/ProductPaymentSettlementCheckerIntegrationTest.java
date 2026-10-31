package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.RefundFailureType;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.WebhookEventStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.facade.ProductPaymentSettlementChecker;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.global.exception.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

// 탈퇴 정산의 PRODUCT_PAYMENT 판정을 실제 MySQL 위에서 본다. 회원 잠금 트랜잭션 안에서 불리는 계약(MANDATORY)이라
// 테스트가 바깥 트랜잭션을 열어 Checker를 직접 부른다. 결제·환불·웹훅은 PaymentFlowIntegrationSupport의 주문·결제 위에
// 엔티티 메서드로 상태를 만든다 — 빌더로 못 만드는 상태는 실제 전이를 거쳐야 한다.
// 스냅숏과 잠금 읽기의 차이(경합)는 ProductPaymentSettlementConcurrencyTest가 맡고, 여기서는 상태별 판정만 본다
class ProductPaymentSettlementCheckerIntegrationTest extends PaymentFlowIntegrationSupport {

    private static final long NO_ORDER_MEMBER_ID = 9_700_001L;

    @Autowired
    ProductPaymentSettlementChecker checker;

    @Autowired
    PaymentService paymentService;

    @Test
    @DisplayName("결제 대기(IN_PROGRESS) 결제가 있으면 PAYMENT-012로 탈퇴를 거절한다")
    void rejectsInProgressPayment() {
        // given
        Flow flow = pendingPayment();

        // when, then
        assertRejected(flow.memberId());
    }

    @Test
    @DisplayName("취소 요청 중(CANCEL_REQUESTED) 결제가 있으면 PAYMENT-012다 — PG 환불 결과를 기다리는 중이다")
    void rejectsCancelRequestedPayment() {
        // given
        Flow flow = paidPaymentWithStock();
        inTransaction(() -> lockedPayment(flow).requestCancel());

        // when, then
        assertRejected(flow.memberId());
    }

    @Test
    @DisplayName("결제 완료(PAID)만 있으면 통과한다")
    void passesPaidPayment() {
        // given
        Flow flow = paidPaymentWithStock();

        // when, then
        assertPassed(flow.memberId());
    }

    @Test
    @DisplayName("실패로 끝난 결제(FAILED)에 환불이 없으면 통과한다")
    void passesFailedPaymentWithoutRefund() {
        // given
        Flow flow = pendingPayment();
        failPayment(flow);

        // when, then
        assertPassed(flow.memberId());
    }

    @Test
    @DisplayName("SYSTEM 환불이 REQUESTED면 PAYMENT-012다 — 늦은 승인 자동 반환이 아직 끝나지 않았다")
    void rejectsRequestedSystemRefund() {
        // given
        Flow flow = pendingPayment();
        failPayment(flow);
        inTransaction(() -> paymentService.requestSystemRefund(lockedPayment(flow), AMOUNT, "늦은 승인 자동 반환"));

        // when, then
        assertRejected(flow.memberId());
    }

    @Test
    @DisplayName("SYSTEM 환불이 FAILED면 PAYMENT-012다 — 돈이 PG에 남은 채 자동 반환이 끝났으니 사람이 봐야 한다")
    void rejectsFailedSystemRefund() {
        // given
        Flow flow = pendingPayment();
        failPayment(flow);
        inTransaction(() -> {
            Refund refund = paymentService.requestSystemRefund(lockedPayment(flow), AMOUNT, "늦은 승인 자동 반환");
            refund.fail(null, RefundFailureType.REQUEST_REJECTED, "PG 거절");
        });

        // when, then
        assertRejected(flow.memberId());
    }

    @ParameterizedTest
    @EnumSource(value = RefundRequester.class, names = {"MEMBER", "ADMIN"})
    @DisplayName("회원·마트 거절 환불이 FAILED로 끝나 결제가 PAID로 돌아갔으면 통과한다 — 다시 취소를 요청할 수 있는 끝난 상태다")
    void passesFailedMemberOrAdminRefund(RefundRequester requester) {
        // given
        Flow flow = paidPaymentWithStock();
        inTransaction(() -> {
            Payment payment = lockedPayment(flow);
            payment.requestCancel();
            Refund refund = requester == RefundRequester.MEMBER
                ? paymentService.requestMemberRefund(payment, CANCEL_REASON, "wd-member-" + flow.orderId())
                : paymentService.requestAdminRefund(payment, "상품 준비가 어렵습니다.", "wd-admin-" + flow.orderId());
            refund.fail(null, RefundFailureType.REQUEST_REJECTED, "PG 거절");
            payment.restorePaid();
        });

        // when, then
        assertPassed(flow.memberId());
    }

    @Test
    @DisplayName("콘솔 전액 취소가 반영된 EXTERNAL 환불 COMPLETED는 통과한다")
    void passesCompletedExternalRefund() {
        // given
        Flow flow = paidPaymentWithStock();
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        inTransaction(() -> {
            Payment payment = lockedPayment(flow);
            payment.markExternallyCanceled(now);
            paymentService.recordExternalRefund(
                payment, "cancel_console_" + flow.orderId(), "콘솔 전액 취소", now.minusMinutes(1), now);
        });

        // when, then
        assertPassed(flow.memberId());
    }

    @ParameterizedTest
    @EnumSource(value = WebhookEventStatus.class, names = {"RECEIVED", "PROCESSING", "RETRY_PENDING", "MANUAL_REVIEW"})
    @DisplayName("반영이 끝나지 않은 웹훅이 있으면 PAYMENT-012다 — 수동 확인 대상도 돈의 행방이 정리되지 않은 것이다")
    void rejectsOpenWebhook(WebhookEventStatus status) {
        // given
        Flow flow = paidPaymentWithStock();
        saveWebhook(flow, status);

        // when, then
        assertRejected(flow.memberId());
    }

    @ParameterizedTest
    @EnumSource(value = WebhookEventStatus.class, names = {"PROCESSED", "IGNORED"})
    @DisplayName("끝난 웹훅만 있으면 통과한다")
    void passesFinishedWebhook(WebhookEventStatus status) {
        // given
        Flow flow = paidPaymentWithStock();
        saveWebhook(flow, status);

        // when, then
        assertPassed(flow.memberId());
    }

    @Test
    @DisplayName("주문이 없는 회원은 통과한다")
    void passesMemberWithoutOrders() {
        // when, then
        assertPassed(NO_ORDER_MEMBER_ID);
    }

    @Test
    @DisplayName("다른 회원의 미해결 결제는 이 회원의 탈퇴를 막지 않는다")
    void ignoresOtherMembersUnsettledPayment() {
        // given
        pendingPayment();
        Flow mine = paidPaymentWithStock();

        // when, then
        assertPassed(mine.memberId());
    }

    @Test
    @DisplayName("바깥 트랜잭션 없이 부르면 거절한다 — 회원 잠금 트랜잭션 안에서만 뜻이 있다")
    void requiresOuterTransaction() {
        // when
        Throwable thrown = catchThrowable(() -> checker.requireSettled(NO_ORDER_MEMBER_ID, 0L));

        // then
        assertThat(thrown).isInstanceOf(IllegalTransactionStateException.class);
    }

    // ---------- 실행 ----------

    // 회원 탈퇴 트랜잭션이 하는 일 중 결제 부분만 바깥 트랜잭션을 열어 부른다. 확인 잔액은 이 Scope가 쓰지 않는다
    private void requireSettled(Long memberId) {
        transactionTemplate.executeWithoutResult(status -> checker.requireSettled(memberId, 0L));
    }

    private void assertRejected(Long memberId) {
        Throwable thrown = catchThrowable(() -> requireSettled(memberId));
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_UNSETTLED_FOR_WITHDRAWAL));
    }

    private void assertPassed(Long memberId) {
        assertThatCode(() -> requireSettled(memberId)).doesNotThrowAnyException();
    }

    // ---------- 픽스처 ----------

    private void inTransaction(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }

    // 결제 상태 변경은 운영 코드와 같이 PAYMENT 잠금 아래에서 한다
    private Payment lockedPayment(Flow flow) {
        return paymentService.getByIdForUpdate(flow.paymentId());
    }

    // 결제 대기 만료로 FAILED. 늦은 승인이 확인되면 SYSTEM 환불이 생기는 자리다
    private void failPayment(Flow flow) {
        inTransaction(() -> lockedPayment(flow).fail(PaymentFailureReason.EXPIRED, null));
    }

    // 원하는 상태의 웹훅 행 하나. 빌더가 없어 수신 → claim → 결과 기록의 실제 전이를 거친다
    private WebhookEvent saveWebhook(Flow flow, WebhookEventStatus status) {
        LocalDateTime now = LocalDateTime.now(clock);
        String webhookId = "wh_settle_" + flow.orderId() + "_" + status;
        if (status == WebhookEventStatus.IGNORED) {
            return webhookEventRepository.save(WebhookEvent.ignored(
                webhookId, "Transaction.Paid", flow.portonePaymentId(), "{}", "다른 상점의 웹훅", now));
        }
        WebhookEvent event = WebhookEvent.received(webhookId, "Transaction.Paid", flow.portonePaymentId(), "{}");
        if (status != WebhookEventStatus.RECEIVED) {
            event.claim(now, Duration.ofMinutes(10));
            switch (status) {
                case PROCESSED -> event.markProcessed(now);
                case RETRY_PENDING -> event.markRetryPending("PortOne 조회 일시 실패");
                case MANUAL_REVIEW -> event.markManualReview("부분 취소", now);
                default -> { } // PROCESSING은 claim까지
            }
        }
        return webhookEventRepository.save(event);
    }
}
