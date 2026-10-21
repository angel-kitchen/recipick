package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.payment.dto.PaymentCreated;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.repository.PaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class PaymentCommandServiceIntegrationTest extends IntegrationTestSupport {

    private static final Long ORDER_ID = 9201L;

    @Autowired
    PaymentCommandService paymentCommandService;
    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanUp() {
        paymentRepository.findByOrderId(ORDER_ID).ifPresent(paymentRepository::delete);
    }

    @Test
    @DisplayName("주문 생성 트랜잭션 안에서 부르면 결제 행이 함께 저장된다")
    void createInsideTransaction() {
        // when
        PaymentCreated created = transactionTemplate.execute(status -> paymentCommandService.create(ORDER_ID, 23800L));

        // then
        Payment saved = paymentRepository.findByOrderId(ORDER_ID).orElseThrow();
        assertThat(saved.getId()).isEqualTo(created.paymentId());
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(saved.getPortonePaymentId()).isEqualTo(created.portonePaymentId());
    }

    @Test
    @DisplayName("주문 생성 트랜잭션이 롤백되면 결제도 함께 롤백된다")
    void rollsBackWithOuterTransaction() {
        // when
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            paymentCommandService.create(ORDER_ID, 23800L);
            throw new IllegalStateException("주문 생성 실패");
        }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("주문 생성 실패");

        // then
        assertThat(paymentRepository.findByOrderId(ORDER_ID)).isEmpty();
    }

    @Test
    @DisplayName("바깥 트랜잭션 없이 부르면 거부한다 — 주문 없는 결제를 만들지 않기 위해")
    void createWithoutTransaction() {
        // when, then
        assertThatThrownBy(() -> paymentCommandService.create(ORDER_ID, 23800L))
            .isInstanceOf(IllegalTransactionStateException.class);
    }
}
