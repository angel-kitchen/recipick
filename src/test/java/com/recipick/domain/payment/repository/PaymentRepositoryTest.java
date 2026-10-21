package com.recipick.domain.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PaymentRepositoryTest extends IntegrationTestSupport {

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    EntityManager em;

    @Test
    @DisplayName("orderId로 결제를 찾고 createdAt이 채워져 있다")
    void findByOrderId() {
        // given
        Payment saved = paymentRepository.save(newPayment(1L));
        em.flush();
        em.clear();

        // when
        Payment found = paymentRepository.findByOrderId(1L).orElseThrow();

        // then
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("잠금 조회도 같은 결제를 돌려준다")
    void findByOrderIdForUpdate() {
        // given
        Payment saved = paymentRepository.save(newPayment(2L));
        em.flush();
        em.clear();

        // when
        Payment found = paymentRepository.findByOrderIdForUpdate(2L).orElseThrow();

        // then
        assertThat(found.getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("enum 세 개가 순서 번호가 아니라 이름 문자열로 저장된다")
    void enumStoredAsString() {
        // given
        Payment payment = newPayment(3L);
        payment.fail(PaymentFailureReason.AMOUNT_MISMATCH, "tx_3");
        Payment saved = paymentRepository.save(payment);
        em.flush();

        // when
        Object status = selectColumn("status", saved.getId());
        Object method = selectColumn("payment_method", saved.getId());
        Object failureReason = selectColumn("failure_reason", saved.getId());

        // then
        assertThat(status).isEqualTo("FAILED");
        assertThat(method).isEqualTo("CARD");
        assertThat(failureReason).isEqualTo("AMOUNT_MISMATCH");
    }

    @Test
    @DisplayName("enum 컬럼이 MySQL에 varchar(20)으로 만들어진다")
    void enumColumnIsVarchar() {
        // when
        Object columnType = em.createNativeQuery(
                "SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'payment' AND COLUMN_NAME = 'status'")
            .getSingleResult();

        // then
        assertThat(columnType).isEqualTo("varchar(20)");
    }

    private Payment newPayment(Long orderId) {
        return Payment.builder()
            .orderId(orderId)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_" + orderId)
            .amount(23800L)
            .status(PaymentStatus.IN_PROGRESS)
            .expiresAt(LocalDateTime.of(2026, 10, 21, 10, 5))
            .build();
    }

    private Object selectColumn(String column, Long id) {
        return em.createNativeQuery("SELECT " + column + " FROM payment WHERE id = :id")
            .setParameter("id", id)
            .getSingleResult();
    }
}
