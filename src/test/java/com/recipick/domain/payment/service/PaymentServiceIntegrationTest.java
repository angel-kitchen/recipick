package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.repository.PaymentRepository;
import com.recipick.domain.payment.repository.RefundRepository;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

// MANDATORY와 UNIQUE 충돌은 Spring 프록시·실제 DB가 있어야 검증된다. 단위 테스트(new PaymentService)로는 보이지 않는다.
// MANDATORY는 "바깥 트랜잭션이 있다"만 강제한다. PAYMENT 잠금을 잡는 책임은 4단계 Processor에 있다
class PaymentServiceIntegrationTest extends IntegrationTestSupport {

    private static final Long ORDER_ID = 9301L;
    private static final Long OTHER_ORDER_ID = 9302L;
    private static final String CLIENT_KEY = "client-key-9301";

    @Autowired
    PaymentService paymentService;
    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    RefundRepository refundRepository;
    @Autowired
    TransactionTemplate transactionTemplate;

    private Long paymentId;
    private Long otherPaymentId;

    @BeforeEach
    void setUp() {
        paymentId = paymentRepository.save(cancelRequestedPayment(ORDER_ID)).getId();
        otherPaymentId = paymentRepository.save(cancelRequestedPayment(OTHER_ORDER_ID)).getId();
    }

    @AfterEach
    void cleanUp() {
        refundRepository.deleteAll(refundRepository.findAllByPaymentIdIn(List.of(paymentId, otherPaymentId)));
        paymentRepository.deleteAllById(List.of(paymentId, otherPaymentId));
    }

    @Test
    @DisplayName("바깥 트랜잭션 안에서 부르면 결제 금액 전액의 MEMBER 환불 행이 클라이언트 키로 저장된다")
    void requestMemberRefundInsideTransaction() {
        // when
        Long refundId = transactionTemplate.execute(status -> {
            Payment payment = paymentRepository.findById(paymentId).orElseThrow();
            return paymentService.requestMemberRefund(payment, "단순 변심", CLIENT_KEY).getId();
        });

        // then — 트랜잭션 밖에서 LAZY 결제를 건드리지 않도록 결제 ID로 다시 찾는다
        List<Refund> ofPayment = refundRepository.findAllByPaymentIdIn(List.of(paymentId));
        assertThat(ofPayment).extracting(Refund::getId).containsExactly(refundId);
        Refund saved = ofPayment.get(0);
        assertThat(saved.getAmount()).isEqualTo(23800L);
        assertThat(saved.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(saved.getRequestedBy()).isEqualTo(RefundRequester.MEMBER);
        assertThat(saved.getIdempotencyKey()).isEqualTo(CLIENT_KEY);
        assertThat(saved.getPortoneCancellationId()).isNull();
    }

    @Test
    @DisplayName("바깥 트랜잭션 없이 부르면 거부한다 — 결제·주문 상태 변경과 따로 커밋되는 환불 행을 만들지 않기 위해")
    void requestMemberRefundWithoutTransaction() {
        // given
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();

        // when, then
        assertThatThrownBy(() -> paymentService.requestMemberRefund(payment, "단순 변심", CLIENT_KEY))
            .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(refundRepository.findByIdempotencyKey(CLIENT_KEY)).isEmpty();
    }

    @Test
    @DisplayName("멱등키 환불 조회는 바깥 트랜잭션 없이 부를 수 없다 — PAYMENT 잠금 아래에서만 뜻이 있는 조회라서")
    void findRefundByIdempotencyKeyWithoutTransaction() {
        // when, then
        assertThatThrownBy(() -> paymentService.findRefundByIdempotencyKey(CLIENT_KEY))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("TX2용 환불 조회는 바깥 트랜잭션 없이 부를 수 없다")
    void getRefundByIdWithoutTransaction() {
        // when, then
        assertThatThrownBy(() -> paymentService.getRefundById(1L))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("다른 결제가 이미 쓴 키로 환불을 만들면 실제 UNIQUE 충돌이 COMMON-011로 바뀌고 행은 한 건만 남는다")
    void requestMemberRefundWithKeyUsedByOtherPayment() {
        // given — 첫 결제가 같은 키로 환불 행을 먼저 커밋했다
        transactionTemplate.executeWithoutResult(status -> {
            Payment payment = paymentRepository.findById(paymentId).orElseThrow();
            paymentService.requestMemberRefund(payment, "단순 변심", CLIENT_KEY);
        });

        // when
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            Payment otherPayment = paymentRepository.findById(otherPaymentId).orElseThrow();
            paymentService.requestMemberRefund(otherPayment, "단순 변심", CLIENT_KEY);
        }))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH));

        // then — 두 번째 결제에는 환불 행이 생기지 않았다
        assertThat(refundRepository.findAllByPaymentIdIn(List.of(paymentId))).hasSize(1);
        assertThat(refundRepository.findAllByPaymentIdIn(List.of(otherPaymentId))).isEmpty();
    }

    private Payment cancelRequestedPayment(Long orderId) {
        return Payment.builder()
            .orderId(orderId)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_service_it_" + orderId)
            .amount(23800L)
            .status(PaymentStatus.CANCEL_REQUESTED)
            .expiresAt(LocalDateTime.of(2026, 10, 21, 10, 5))
            .build();
    }
}
