package com.recipick.domain.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundFailureType;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
    @DisplayName("같은 결제에 MEMBER 환불이 있어도 SYSTEM 환불만 조회한다")
    void findSystemRefundByPaymentId() {
        // given
        Payment payment = paymentRepository.save(newFailedPayment(1L));
        Refund system = refundRepository.save(Refund.requestedBySystem(
            payment, 23800L, "결제 금액 불일치", "key_1_system",
            LocalDateTime.of(2026, 10, 21, 10, 10)));
        refundRepository.save(newMemberRefund(payment, "key_1_member"));
        em.flush();
        em.clear();

        // when
        Refund found = refundRepository.findSystemRefundByPaymentId(payment.getId()).orElseThrow();

        // then
        assertThat(found.getId()).isEqualTo(system.getId());
        assertThat(found.getRequestedBy()).isEqualTo(RefundRequester.SYSTEM);
        assertThat(found.getStatus()).isEqualTo(RefundStatus.REQUESTED);
    }

    @Test
    @DisplayName("MEMBER 환불만 있는 결제는 SYSTEM 환불 조회가 비어 있다")
    void findSystemRefundByPaymentIdWhenOnlyMember() {
        // given
        Payment payment = paymentRepository.save(newFailedPayment(3L));
        refundRepository.save(newMemberRefund(payment, "key_3_member"));
        em.flush();
        em.clear();

        // when
        Optional<Refund> found = refundRepository.findSystemRefundByPaymentId(payment.getId());

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("멱등키로 환불 한 건을 찾고, 없는 키는 비어 있다")
    void findByIdempotencyKey() {
        // given
        Payment payment = paymentRepository.save(newFailedPayment(9L));
        Refund saved = refundRepository.save(newMemberRefund(payment, "key_9_member"));
        em.flush();
        em.clear();

        // when
        Optional<Refund> found = refundRepository.findByIdempotencyKey("key_9_member");
        Optional<Refund> missing = refundRepository.findByIdempotencyKey("key_9_unknown");

        // then
        assertThat(found).map(Refund::getId).contains(saved.getId());
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("같은 멱등키로 두 번째 환불을 flush하면 UNIQUE에 걸린다 — 다른 결제에 같은 키가 동시에 와도 한 건만 남는다")
    void idempotencyKeyIsUnique() {
        // given
        Payment payment = paymentRepository.save(newFailedPayment(10L));
        Payment otherPayment = paymentRepository.save(newFailedPayment(11L));
        refundRepository.saveAndFlush(newMemberRefund(payment, "key_shared"));

        // when
        Throwable thrown = catchThrowable(() ->
            refundRepository.saveAndFlush(newMemberRefund(otherPayment, "key_shared")));

        // then
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("환불 실패 종류가 이름 문자열로 저장된다")
    void failureTypeStoredAsString() {
        // given
        Payment payment = paymentRepository.save(newFailedPayment(12L));
        Refund refund = newMemberRefund(payment, "key_12");
        refund.fail(null, RefundFailureType.REQUEST_REJECTED, "HTTP 401 UNAUTHORIZED");
        Refund saved = refundRepository.save(refund);
        em.flush();

        // when
        Object failureType = selectColumn("failure_type", saved.getId());

        // then
        assertThat(failureType).isEqualTo("REQUEST_REJECTED");
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

    @Test
    @DisplayName("결제 하나의 환불 이력을 최신 생성 순으로 조회하고 다른 결제의 환불은 제외한다")
    void findAllByPaymentIdOrderByIdDesc() {
        // given — 실패한 시도 뒤에 완료된 시도가 생긴 결제와, 섞이면 안 되는 다른 결제
        Payment payment = paymentRepository.save(newFailedPayment(4L));
        Payment otherPayment = paymentRepository.save(newFailedPayment(5L));
        Refund first = refundRepository.save(newMemberRefund(payment, "key_4_first", RefundStatus.FAILED));
        Refund second = refundRepository.save(newMemberRefund(payment, "key_4_second", RefundStatus.COMPLETED));
        refundRepository.save(newMemberRefund(otherPayment, "key_5_other", RefundStatus.REQUESTED));
        em.flush();
        em.clear();

        // when
        List<Refund> found = refundRepository.findAllByPaymentIdOrderByIdDesc(payment.getId());

        // then
        assertThat(found).extracting(Refund::getId).containsExactly(second.getId(), first.getId());
    }

    @Test
    @DisplayName("여러 결제의 환불을 한 번에 조회하고 목록에 없는 결제의 환불은 제외한다")
    void findAllByPaymentIdIn() {
        // given
        Payment first = paymentRepository.save(newFailedPayment(6L));
        Payment second = paymentRepository.save(newFailedPayment(7L));
        Payment excluded = paymentRepository.save(newFailedPayment(8L));
        refundRepository.save(newMemberRefund(first, "key_6", RefundStatus.COMPLETED));
        refundRepository.save(newMemberRefund(second, "key_7_a", RefundStatus.FAILED));
        refundRepository.save(newMemberRefund(second, "key_7_b", RefundStatus.COMPLETED));
        refundRepository.save(newMemberRefund(excluded, "key_8", RefundStatus.REQUESTED));
        em.flush();
        em.clear();

        // when
        List<Refund> found = refundRepository.findAllByPaymentIdIn(List.of(first.getId(), second.getId()));

        // then — 두 결제의 환불 3건만, 8번 결제 것은 빠진다
        assertThat(found).hasSize(3);
        assertThat(found).extracting(refund -> refund.getPayment().getId())
            .containsOnly(first.getId(), second.getId());
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

    private Refund newMemberRefund(Payment payment, String idempotencyKey) {
        return newMemberRefund(payment, idempotencyKey, RefundStatus.REQUESTED);
    }

    private Refund newMemberRefund(Payment payment, String idempotencyKey, RefundStatus status) {
        return Refund.builder()
            .payment(payment)
            .amount(23800L)
            .reason("회원 취소")
            .status(status)
            .requestedBy(RefundRequester.MEMBER)
            .idempotencyKey(idempotencyKey)
            .requestedAt(LocalDateTime.of(2026, 10, 21, 10, 10))
            .build();
    }

    private Object selectColumn(String column, Long id) {
        return em.createNativeQuery("SELECT " + column + " FROM refund WHERE id = :id")
            .setParameter("id", id)
            .getSingleResult();
    }
}
