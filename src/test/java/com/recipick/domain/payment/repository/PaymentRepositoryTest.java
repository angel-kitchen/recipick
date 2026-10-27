package com.recipick.domain.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PaymentRepositoryTest extends IntegrationTestSupport {

    // Facade가 넘기는 것과 같은 정렬
    private static final Sort HISTORY_SORT =
        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

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
    @DisplayName("결제 ID 잠금 조회도 같은 결제를 돌려준다")
    void findByIdForUpdate() {
        // given
        Payment saved = paymentRepository.save(newPayment(11L));
        em.flush();
        em.clear();

        // when
        Payment found = paymentRepository.findByIdForUpdate(saved.getId()).orElseThrow();

        // then
        assertThat(found.getOrderId()).isEqualTo(11L);
    }

    @Test
    @DisplayName("PortOne 결제 ID로 결제 한 건을 찾고, 모르는 ID는 비어 있다 — 웹훅이 들어오는 경로")
    void findByPortonePaymentId() {
        // given
        Payment saved = paymentRepository.save(newPayment(12L));
        em.flush();
        em.clear();

        // when
        Payment found = paymentRepository.findByPortonePaymentId("pay_12").orElseThrow();
        Optional<Payment> unknown = paymentRepository.findByPortonePaymentId("pay_unknown");

        // then
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getOrderId()).isEqualTo(12L);
        assertThat(unknown).isEmpty();
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

    @Test
    @DisplayName("주문 ID 목록의 결제만 페이지로 조회하고 다른 주문의 결제는 제외한다")
    void findByOrderIdIn() {
        // given — 4·5가 내 주문, 6은 남의 주문
        Payment first = paymentRepository.save(newPayment(4L));
        Payment second = paymentRepository.save(newPayment(5L));
        paymentRepository.save(newPayment(6L));
        em.flush();
        em.clear();

        // when — 첫 페이지 1건
        Page<Payment> page = paymentRepository.findByOrderIdIn(
            List.of(4L, 5L), PageRequest.of(0, 1, HISTORY_SORT));

        // then — 전체 건수는 2건이고 6번 결제는 들어오지 않는다
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);

        // when — 두 페이지를 합치면 내 결제 두 건이 모두 나온다
        Page<Payment> secondPage = paymentRepository.findByOrderIdIn(
            List.of(4L, 5L), PageRequest.of(1, 1, HISTORY_SORT));

        // then
        assertThat(List.of(page.getContent().get(0).getId(), secondPage.getContent().get(0).getId()))
            .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    @DisplayName("createdAt이 같은 결제는 id 내림차순으로 나온다")
    void findByOrderIdInBreaksTieById() {
        // given — 두 결제의 생성 시각을 같은 값으로 맞춘다. createdAt만으로는 순서가 정해지지 않는 상황
        Payment first = paymentRepository.save(newPayment(7L));
        Payment second = paymentRepository.save(newPayment(8L));
        em.flush();
        forceCreatedAt(LocalDateTime.of(2026, 10, 23, 9, 0), first.getId(), second.getId());
        em.clear();

        // when
        Page<Payment> page = paymentRepository.findByOrderIdIn(
            List.of(7L, 8L), PageRequest.of(0, 2, HISTORY_SORT));

        // then — 나중에 만든 second가 먼저 온다. 보조 정렬이 없으면 순서가 흔들린다
        assertThat(page.getContent()).extracting(Payment::getId)
            .containsExactly(second.getId(), first.getId());
    }

    @Test
    @DisplayName("createdAt이 다르면 id가 작아도 생성 시각이 최신인 결제가 먼저 나온다")
    void findByOrderIdInSortsByCreatedAtFirst() {
        // given — id는 first가 작지만 createdAt은 first가 더 늦다. 두 기준이 반대를 가리키는 상황
        Payment first = paymentRepository.save(newPayment(9L));
        Payment second = paymentRepository.save(newPayment(10L));
        em.flush();
        forceCreatedAt(LocalDateTime.of(2026, 10, 23, 9, 5), first.getId());
        forceCreatedAt(LocalDateTime.of(2026, 10, 23, 9, 0), second.getId());
        em.clear();

        // when
        Page<Payment> page = paymentRepository.findByOrderIdIn(
            List.of(9L, 10L), PageRequest.of(0, 2, HISTORY_SORT));

        // then — id가 큰 second가 아니라 createdAt이 최신인 first가 앞이다. createdAt이 1순위다
        assertThat(page.getContent()).extracting(Payment::getId)
            .containsExactly(first.getId(), second.getId());
    }

    @Test
    @DisplayName("만료 재조회 구간이 지난 IN_PROGRESS 결제만 경계 포함·ID 순으로 이어 읽는다 — 결제 완료·실패·만료 시각 없음은 제외")
    void findStaleInProgressPaymentIds() {
        // given — 기준 시각을 먼 과거로 잡는다. 다른 통합 테스트가 커밋해 둔 결제는 실제 현재 시각 근처라 섞이지 않는다
        LocalDateTime expiredBefore = LocalDateTime.of(2000, 1, 1, 10, 0);
        Payment before = paymentRepository.save(inProgressPayment(13L, expiredBefore.minusMinutes(1)));
        Payment boundary = paymentRepository.save(inProgressPayment(14L, expiredBefore));
        paymentRepository.save(inProgressPayment(15L, expiredBefore.plusMinutes(1)));
        Payment paid = inProgressPayment(16L, expiredBefore.minusHours(1));
        paid.markPaid("tx_16", expiredBefore.minusHours(1).minusMinutes(1));
        paymentRepository.save(paid);
        Payment failed = inProgressPayment(17L, expiredBefore.minusHours(1));
        failed.fail(PaymentFailureReason.EXPIRED, null);
        paymentRepository.save(failed);
        paymentRepository.save(inProgressPayment(18L, null));
        em.flush();
        em.clear();

        // when — 한 건씩 읽은 뒤 마지막 ID 다음부터 이어 읽는다
        List<Long> firstPage = paymentRepository.findStaleInProgressPaymentIds(
            0L, expiredBefore, PageRequest.ofSize(1));
        List<Long> secondPage = paymentRepository.findStaleInProgressPaymentIds(
            firstPage.get(0), expiredBefore, PageRequest.ofSize(10));

        // then
        assertThat(firstPage).containsExactly(before.getId());
        assertThat(secondPage).containsExactly(boundary.getId());
    }

    private Payment inProgressPayment(Long orderId, LocalDateTime expiresAt) {
        return Payment.builder()
            .orderId(orderId)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_" + orderId)
            .amount(23800L)
            .status(PaymentStatus.IN_PROGRESS)
            .expiresAt(expiresAt)
            .build();
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

    // createdAt은 @Column(updatable = false)라 JPA로 못 바꾼다. 정렬 경계 상황을 만들려면 네이티브로 직접 쓴다
    private void forceCreatedAt(LocalDateTime createdAt, Long... paymentIds) {
        for (Long paymentId : paymentIds) {
            em.createNativeQuery("UPDATE payment SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", paymentId)
                .executeUpdate();
        }
    }

    private Object selectColumn(String column, Long id) {
        return em.createNativeQuery("SELECT " + column + " FROM payment WHERE id = :id")
            .setParameter("id", id)
            .getSingleResult();
    }
}
