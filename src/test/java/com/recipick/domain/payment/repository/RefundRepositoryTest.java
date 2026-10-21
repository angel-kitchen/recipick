package com.recipick.domain.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RefundRepositoryTest extends IntegrationTestSupport {

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    RefundRepository refundRepository;

    @Autowired
    EntityManager em;

    @Test
    @DisplayName("결제 ID와 요청 주체로 SYSTEM 환불을 찾고, 다른 주체로는 찾지 못한다")
    void findByPaymentIdAndRequestedBy() {
        // given
        Payment payment = paymentRepository.save(newFailedPayment(1L));
        Refund saved = refundRepository.save(Refund.requestedBySystem(
            payment, 23800L, "결제 금액 불일치", "key_1",
            LocalDateTime.of(2026, 10, 21, 10, 10)));
        em.flush();
        em.clear();

        // when
        Refund found = refundRepository
            .findByPaymentIdAndRequestedBy(payment.getId(), RefundRequester.SYSTEM)
            .orElseThrow();

        // then
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refundRepository
            .findByPaymentIdAndRequestedBy(payment.getId(), RefundRequester.MEMBER))
            .isEmpty();
    }

    @Test
    @DisplayName("Refund의 enum 두 개가 이름 문자열로 저장된다")
    void enumStoredAsString() {
        // given
        Payment payment = paymentRepository.save(newFailedPayment(2L));
        Refund saved = refundRepository.save(Refund.requestedBySystem(
            payment, 23800L, "결제 금액 불일치", "key_2",
            LocalDateTime.of(2026, 10, 21, 10, 10)));
        em.flush();

        // when
        Object status = selectColumn("status", saved.getId());
        Object requestedBy = selectColumn("requested_by", saved.getId());

        // then
        assertThat(status).isEqualTo("REQUESTED");
        assertThat(requestedBy).isEqualTo("SYSTEM");
    }

    private Payment newFailedPayment(Long orderId) {
        return Payment.builder()
            .orderId(orderId)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_" + orderId)
            .amount(23800L)
            .status(PaymentStatus.FAILED)
            .expiresAt(LocalDateTime.of(2026, 10, 21, 10, 5))
            .build();
    }

    private Object selectColumn(String column, Long id) {
        return em.createNativeQuery("SELECT " + column + " FROM refund WHERE id = :id")
            .setParameter("id", id)
            .getSingleResult();
    }
}
