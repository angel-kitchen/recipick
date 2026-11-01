package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.service.MembershipPaymentAttemptRef;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipPaymentAttemptRepository extends JpaRepository<MembershipPaymentAttempt, Long> {

    // C-17 ②. 연관 경로(membershipPayment.membershipId)를 타는 파생 쿼리
    boolean existsByMembershipPaymentMembershipIdInAndStatusIn(Collection<Long> membershipIds,
                                                               Collection<MembershipPaymentAttemptStatus> statuses);

    // 삭제 보류 판정(I-17). "이 키를 쓰는 미해결 시도가 있나". 테스트(BK-10)만 부른다
    boolean existsByBillingKeyIdAndStatusIn(Long billingKeyId, Collection<MembershipPaymentAttemptStatus> statuses);

    // C-17 ④. PAID인데 돌려줘야 하는 시도 중 그 시도의 SYSTEM 환불이 COMPLETED가 아닌 것.
    // MEMBER 환불 COMPLETED는 해제하지 않는다 — 돌려줘야 할 돈은 SYSTEM 환불로만 돌아간다
    @Query("""
        SELECT COUNT(a) > 0
          FROM MembershipPaymentAttempt a
         WHERE a.membershipPayment.membershipId IN :membershipIds
           AND a.status = com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus.PAID
           AND a.refundRequiredAt IS NOT NULL
           AND NOT EXISTS (
               SELECT 1 FROM MembershipRefund r
                WHERE r.membershipPaymentAttempt = a
                  AND r.requestedBy = com.recipick.domain.payment.entity.enums.RefundRequester.SYSTEM
                  AND r.status = com.recipick.domain.payment.entity.enums.MembershipRefundStatus.COMPLETED)
        """)
    boolean existsPaidRequiringRefundWithoutCompletedSystemRefund(
        @Param("membershipIds") Collection<Long> membershipIds);

    // 선점 TX·TX2. 존재하는 행만 PK로 잠근다(시도 PK가 잠금 순서의 마지막)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM MembershipPaymentAttempt a WHERE a.id = :id")
    Optional<MembershipPaymentAttempt> findByIdForUpdate(@Param("id") Long id);

    // 잠금 전 ID·값만 읽는다(일반 조회). 엔티티를 잠금 전에 올려 두면 잠금 조회가 영속성 컨텍스트의 옛 인스턴스를 돌려주므로
    // 실행기·TX2·복구는 이 투영으로 회차·이용권 ID와 결제 ID를 얻고, 변경은 잠금 조회로 다시 읽은 엔티티에만 한다
    @Query("""
        SELECT new com.recipick.domain.payment.service.MembershipPaymentAttemptRef(
            a.id, a.membershipPayment.id, a.membershipPayment.membershipId, a.portonePaymentId,
            a.membershipPayment.amount, a.status, a.dispatchedAt, a.leaseExpiresAt, a.failureReason,
            a.opsReviewRequiredAt)
          FROM MembershipPaymentAttempt a
         WHERE a.id = :id
        """)
    Optional<MembershipPaymentAttemptRef> findRef(@Param("id") Long id);

    // 같은 키 재요청의 상태 판정: 회차의 최신 시도
    Optional<MembershipPaymentAttempt> findTopByMembershipPaymentIdOrderByAttemptNoDesc(Long membershipPaymentId);

    // READY 만료 재검사: 회차의 모든 시도. 회차 PK를 잠근 뒤 읽는다
    List<MembershipPaymentAttempt> findByMembershipPaymentIdOrderByAttemptNoAsc(Long membershipPaymentId);

    // 접수 유효 창의 기준: 회차의 첫 시도 requestedAt. 선점 TX가 티켓의 acceptUntil을 계산할 때 읽는다
    Optional<MembershipPaymentAttempt> findTopByMembershipPaymentIdOrderByAttemptNoAsc(Long membershipPaymentId);

    // 복구 대상 조회(keyset, 잠금 없음). 선점이 오래된 시도: dispatchedAt < now - (L + M).
    // 자동 정합화가 멈춘 시도(정합성 코드 APPLIED_STATE_INCONSISTENT + 운영 표시 — MembershipPaymentAttempt.isAutoReconcileHalted와
    // 같은 조건)만 뺀다. 다른 사유의 운영 표시가 있는 시도는 그대로 자동 재조회한다
    @Query("""
        SELECT new com.recipick.domain.payment.service.MembershipPaymentAttemptRef(
            a.id, a.membershipPayment.id, a.membershipPayment.membershipId, a.portonePaymentId,
            a.membershipPayment.amount, a.status, a.dispatchedAt, a.leaseExpiresAt, a.failureReason,
            a.opsReviewRequiredAt)
          FROM MembershipPaymentAttempt a
         WHERE a.status = com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus.DISPATCHING
           AND a.dispatchedAt < :before
           AND (a.failureReason IS NULL OR a.failureReason <> 'APPLIED_STATE_INCONSISTENT'
                OR a.opsReviewRequiredAt IS NULL)
           AND a.id > :afterId
         ORDER BY a.id
        """)
    List<MembershipPaymentAttemptRef> findStaleDispatching(@Param("before") LocalDateTime before,
                                                           @Param("afterId") Long afterId, Pageable pageable);

    // 복구 대상 조회. 결과 불명 시도: 마지막 갱신 뒤 M이 지난 것(자동 정합화가 멈춘 시도 제외 — 위와 같음)
    @Query("""
        SELECT new com.recipick.domain.payment.service.MembershipPaymentAttemptRef(
            a.id, a.membershipPayment.id, a.membershipPayment.membershipId, a.portonePaymentId,
            a.membershipPayment.amount, a.status, a.dispatchedAt, a.leaseExpiresAt, a.failureReason,
            a.opsReviewRequiredAt)
          FROM MembershipPaymentAttempt a
         WHERE a.status = com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus.UNKNOWN
           AND a.updatedAt < :before
           AND (a.failureReason IS NULL OR a.failureReason <> 'APPLIED_STATE_INCONSISTENT'
                OR a.opsReviewRequiredAt IS NULL)
           AND a.id > :afterId
         ORDER BY a.id
        """)
    List<MembershipPaymentAttemptRef> findUnknownUpdatedBefore(@Param("before") LocalDateTime before,
                                                               @Param("afterId") Long afterId, Pageable pageable);

    // 복구 대상 조회. READY인 채 임대가 끝난 시도(PG 미발송 보장)
    @Query("""
        SELECT new com.recipick.domain.payment.service.MembershipPaymentAttemptRef(
            a.id, a.membershipPayment.id, a.membershipPayment.membershipId, a.portonePaymentId,
            a.membershipPayment.amount, a.status, a.dispatchedAt, a.leaseExpiresAt, a.failureReason,
            a.opsReviewRequiredAt)
          FROM MembershipPaymentAttempt a
         WHERE a.status = com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus.READY
           AND a.leaseExpiresAt <= :now
           AND a.id > :afterId
         ORDER BY a.id
        """)
    List<MembershipPaymentAttemptRef> findExpiredReady(@Param("now") LocalDateTime now,
                                                       @Param("afterId") Long afterId, Pageable pageable);
}
