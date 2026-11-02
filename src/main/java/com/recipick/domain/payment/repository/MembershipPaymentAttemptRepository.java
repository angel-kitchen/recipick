package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.MembershipPaymentAttempt;
import com.recipick.domain.payment.entity.enums.MembershipOpsReviewReason;
import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import com.recipick.domain.payment.service.BillingKeyRecheckRef;
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
            a.opsReviewRequiredAt, a.opsReviewReason)
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
    // 강한 정합성 차단 표시가 있는 시도가 하나라도 있는 회차는 통째로 뺀다(차단은 회차 단위다 — 다른 시도의 결과를 반영해
    // 차단을 우회하지 않는다). 다른 사유의 운영 표시가 있는 시도는 그대로 자동 재조회한다. blocking = MembershipOpsReviewReason.BLOCKING
    @Query("""
        SELECT new com.recipick.domain.payment.service.MembershipPaymentAttemptRef(
            a.id, a.membershipPayment.id, a.membershipPayment.membershipId, a.portonePaymentId,
            a.membershipPayment.amount, a.status, a.dispatchedAt, a.leaseExpiresAt, a.failureReason,
            a.opsReviewRequiredAt, a.opsReviewReason)
          FROM MembershipPaymentAttempt a
         WHERE a.status = com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus.DISPATCHING
           AND a.dispatchedAt < :before
           AND NOT EXISTS (
               SELECT b.id FROM MembershipPaymentAttempt b
                WHERE b.membershipPayment.id = a.membershipPayment.id
                  AND b.opsReviewReason IN :blocking)
           AND a.id > :afterId
         ORDER BY a.id
        """)
    List<MembershipPaymentAttemptRef> findStaleDispatching(
        @Param("before") LocalDateTime before,
        @Param("blocking") Collection<MembershipOpsReviewReason> blocking,
        @Param("afterId") Long afterId,
        Pageable pageable);

    // 복구 대상 조회. 결과 불명 시도: 마지막 갱신 뒤 M이 지난 것(차단 회차 제외 — 위와 같음)
    @Query("""
        SELECT new com.recipick.domain.payment.service.MembershipPaymentAttemptRef(
            a.id, a.membershipPayment.id, a.membershipPayment.membershipId, a.portonePaymentId,
            a.membershipPayment.amount, a.status, a.dispatchedAt, a.leaseExpiresAt, a.failureReason,
            a.opsReviewRequiredAt, a.opsReviewReason)
          FROM MembershipPaymentAttempt a
         WHERE a.status = com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus.UNKNOWN
           AND a.updatedAt < :before
           AND NOT EXISTS (
               SELECT b.id FROM MembershipPaymentAttempt b
                WHERE b.membershipPayment.id = a.membershipPayment.id
                  AND b.opsReviewReason IN :blocking)
           AND a.id > :afterId
         ORDER BY a.id
        """)
    List<MembershipPaymentAttemptRef> findUnknownUpdatedBefore(
        @Param("before") LocalDateTime before,
        @Param("blocking") Collection<MembershipOpsReviewReason> blocking,
        @Param("afterId") Long afterId,
        Pageable pageable);

    // 복구 대상 조회. READY인 채 임대가 끝난 시도(PG 미발송 보장). 차단 회차의 READY는 뺀다 — 미발송 보장에 반하는 증거가 있는
    // 회차의 READY를 자동으로 CANCELED로 접거나 새 시도를 만들지 않고 운영 확인 대상으로 보존한다
    @Query("""
        SELECT new com.recipick.domain.payment.service.MembershipPaymentAttemptRef(
            a.id, a.membershipPayment.id, a.membershipPayment.membershipId, a.portonePaymentId,
            a.membershipPayment.amount, a.status, a.dispatchedAt, a.leaseExpiresAt, a.failureReason,
            a.opsReviewRequiredAt, a.opsReviewReason)
          FROM MembershipPaymentAttempt a
         WHERE a.status = com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus.READY
           AND a.leaseExpiresAt <= :now
           AND NOT EXISTS (
               SELECT b.id FROM MembershipPaymentAttempt b
                WHERE b.membershipPayment.id = a.membershipPayment.id
                  AND b.opsReviewReason IN :blocking)
           AND a.id > :afterId
         ORDER BY a.id
        """)
    List<MembershipPaymentAttemptRef> findExpiredReady(
        @Param("now") LocalDateTime now,
        @Param("blocking") Collection<MembershipOpsReviewReason> blocking,
        @Param("afterId") Long afterId,
        Pageable pageable);

    // 빌링키 재검사 후보: 결과 불명 창 안에서 404(PAYMENT_NOT_FOUND)로 남은 UNKNOWN 시도에 묶인 ACTIVE·REPLACED 키. 키 ID keyset.
    // 키가 없거나 삭제돼 PortOne이 결제를 만들지 않았을 수 있는 시도만이다 — 전체 회원·전체 키를 조회하지 않는다
    @Query("""
        SELECT DISTINCT new com.recipick.domain.payment.service.BillingKeyRecheckRef(
            a.billingKey.id, a.billingKey.memberId)
          FROM MembershipPaymentAttempt a
         WHERE a.status = com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus.UNKNOWN
           AND a.failureReason = 'PAYMENT_NOT_FOUND'
           AND a.dispatchedAt >= :since
           AND a.billingKey.status IN (com.recipick.domain.payment.entity.enums.BillingKeyStatus.ACTIVE,
                                       com.recipick.domain.payment.entity.enums.BillingKeyStatus.REPLACED)
           AND a.billingKey.id > :afterId
         ORDER BY a.billingKey.id
        """)
    List<BillingKeyRecheckRef> findBillingKeysToRecheck(@Param("since") LocalDateTime since,
                                                        @Param("afterId") Long afterId, Pageable pageable);

    // 회차 단위 강한 차단 조회(일반 조회, 잠금 없음). 선점 TX·재조회 진입·POST 직전이 "이 회차에 차단 시도가 있나"를 본다.
    // 파생 쿼리: membershipPayment.id IN 조건
    boolean existsByMembershipPaymentIdAndOpsReviewReasonIn(Long membershipPaymentId,
                                                            Collection<MembershipOpsReviewReason> reasons);

    // C-17 ⑤. 상태와 무관하게 강한 정합성 차단 표시가 있는 시도(CANCELED·PAID·FAILED여도 막는다)
    boolean existsByMembershipPaymentMembershipIdInAndOpsReviewReasonIn(Collection<Long> membershipIds,
                                                                        Collection<MembershipOpsReviewReason> reasons);

    // 웹훅 라우팅 매핑(UNIQUE portone_payment_id). 접두어는 힌트일 뿐이고 매핑은 정확 일치다
    @Query("""
        SELECT new com.recipick.domain.payment.service.MembershipPaymentAttemptRef(
            a.id, a.membershipPayment.id, a.membershipPayment.membershipId, a.portonePaymentId,
            a.membershipPayment.amount, a.status, a.dispatchedAt, a.leaseExpiresAt, a.failureReason,
            a.opsReviewRequiredAt, a.opsReviewReason)
          FROM MembershipPaymentAttempt a
         WHERE a.portonePaymentId = :portonePaymentId
        """)
    Optional<MembershipPaymentAttemptRef> findRefByPortonePaymentId(@Param("portonePaymentId") String portonePaymentId);

    // 갱신 회차의 시도들을 잠그기 전 ID만 읽는다. 호출자는 회차 PK를 잠근 뒤 이 순서(PK 오름차순)대로 findByIdForUpdate로
    // 한 행씩 잠근다 — 범위 조건의 FOR UPDATE로 여러 행을 한 번에 잠그지 않는다
    @Query("""
        SELECT a.id
          FROM MembershipPaymentAttempt a
         WHERE a.membershipPayment.id = :paymentId
         ORDER BY a.id
        """)
    List<Long> findIdsByMembershipPaymentIdOrderByIdAsc(@Param("paymentId") Long paymentId);
}
