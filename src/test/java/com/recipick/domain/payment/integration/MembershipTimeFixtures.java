package com.recipick.domain.payment.integration;

import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.transaction.support.TransactionTemplate;

// 시각 픽스처는 운영과 같은 저장 경로(Hibernate, jdbc.time_zone=Asia/Seoul)로만 쓴다 — JDBC로 직접 쓰고 JPA로 읽으면 JVM 시간대가
// UTC일 때 값이 어긋날 수 있다. 짧은 트랜잭션의 JPQL 벌크 UPDATE·엔티티 저장만 쓴다.
// 운영 엔티티에 테스트용 setter를 만들지 않으려고 JPQL로 바꾼다. 벌크 UPDATE는 영속성 컨텍스트를 거치지 않으므로 이후 읽기는 새로 조회한다
final class MembershipTimeFixtures {

    private final EntityManager em;
    private final TransactionTemplate tx;

    MembershipTimeFixtures(EntityManager em, TransactionTemplate tx) {
        this.em = em;
        this.tx = tx;
    }

    void attemptDispatchedAt(Long attemptId, LocalDateTime dispatchedAt) {
        update("UPDATE MembershipPaymentAttempt a SET a.dispatchedAt = :t WHERE a.id = :id", dispatchedAt, attemptId);
    }

    void attemptLeaseExpiresAt(Long attemptId, LocalDateTime leaseExpiresAt) {
        update("UPDATE MembershipPaymentAttempt a SET a.leaseExpiresAt = :t WHERE a.id = :id", leaseExpiresAt,
            attemptId);
    }

    // 만료 분기 재현용: 접힌(CANCELED) 시도를 임대가 끝난 READY로 되돌린다
    void attemptBackToExpiredReady(Long attemptId, LocalDateTime leaseExpiresAt) {
        tx.executeWithoutResult(s -> em.createQuery("""
                UPDATE MembershipPaymentAttempt a
                   SET a.status = com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus.READY,
                       a.leaseExpiresAt = :t, a.completedAt = NULL, a.failureReason = NULL, a.dispatchedAt = NULL
                 WHERE a.id = :id
                """)
            .setParameter("t", leaseExpiresAt).setParameter("id", attemptId).executeUpdate());
    }

    // 다른 사유의 운영 표시(자동 정합화 중단과 무관한 표시)를 붙인다
    void attemptOpsFlag(Long attemptId, LocalDateTime at, MembershipOpsReviewReason reason) {
        tx.executeWithoutResult(s -> em.createQuery("""
                UPDATE MembershipPaymentAttempt a SET a.opsReviewRequiredAt = :t, a.opsReviewReason = :r
                 WHERE a.id = :id
                """)
            .setParameter("t", at).setParameter("r", reason).setParameter("id", attemptId).executeUpdate());
    }

    // 회차를 확정 상태로 바꾼다(확정 회차에 남은 READY 재현용)
    void paymentStatus(Long membershipPaymentId, MembershipPaymentStatus status) {
        tx.executeWithoutResult(s -> em.createQuery("UPDATE MembershipPayment p SET p.status = :s WHERE p.id = :id")
            .setParameter("s", status).setParameter("id", membershipPaymentId).executeUpdate());
    }

    void membershipActivated(Long membershipId, LocalDateTime activatedAt) {
        tx.executeWithoutResult(s -> em.createQuery(
                "UPDATE Membership m SET m.activatedAt = :t, m.startAt = :t WHERE m.id = :id")
            .setParameter("t", activatedAt).setParameter("id", membershipId).executeUpdate());
    }

    // 활성화 표시만 지운다(0b 원인이 해소된 상황 재현)
    void membershipActivationCleared(Long membershipId) {
        tx.executeWithoutResult(s -> em.createQuery("UPDATE Membership m SET m.activatedAt = NULL WHERE m.id = :id")
            .setParameter("id", membershipId).executeUpdate());
    }

    void membershipActivatedFor(Long membershipId, LocalDateTime activatedAt, LocalDateTime endAt,
                                LocalDateTime graceEndAt) {
        tx.executeWithoutResult(s -> em.createQuery("""
                UPDATE Membership m SET m.activatedAt = :a, m.startAt = :a, m.endAt = :e, m.graceEndAt = :g
                 WHERE m.id = :id
                """)
            .setParameter("a", activatedAt).setParameter("e", endAt).setParameter("g", graceEndAt)
            .setParameter("id", membershipId).executeUpdate());
    }

    void membershipTerminated(Long membershipId, LocalDateTime terminatedAt) {
        update("UPDATE Membership m SET m.terminatedAt = :t WHERE m.id = :id", terminatedAt, membershipId);
    }

    // 같은 이용권의 다른 회차(갱신형 — graceDeadlineAt non-null). 확정 FAILED 또는 반영된 PAID
    Long otherPayment(Long membershipId, long amount, MembershipPaymentStatus status, LocalDate periodStart,
                      LocalDateTime graceDeadlineAt, LocalDateTime paidAt, String failureReason) {
        return tx.execute(s -> {
            MembershipPayment payment = MembershipPayment.builder().membershipId(membershipId).amount(amount)
                .status(status).billingDate(periodStart).periodStartDate(periodStart)
                .periodEndDate(periodStart.plusMonths(1).minusDays(1)).graceDeadlineAt(graceDeadlineAt)
                .paidAt(paidAt).membershipAppliedAt(paidAt).failureReason(failureReason).build();
            em.persist(payment);
            em.flush();
            return payment.getId();
        });
    }

    void deletePayment(Long membershipPaymentId) {
        tx.executeWithoutResult(s -> em.createQuery("DELETE FROM MembershipPayment p WHERE p.id = :id")
            .setParameter("id", membershipPaymentId).executeUpdate());
    }

    private void update(String jpql, LocalDateTime value, Long id) {
        tx.executeWithoutResult(s -> em.createQuery(jpql).setParameter("t", value).setParameter("id", id)
            .executeUpdate());
    }
}
