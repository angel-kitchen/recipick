package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.MembershipRefund;
import com.recipick.domain.payment.entity.enums.MembershipRefundStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.service.MembershipRefundRef;
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

public interface MembershipRefundRepository extends JpaRepository<MembershipRefund, Long> {

    // 환불 결과를 기다리는 중. 회차 직접 참조(membership_payment_id)는 NULL을 허용하므로 쓰지 않고,
    // 항상 채워지는 시도 FK(membership_payment_attempt_id) → 그 시도의 회차 → 이용권 경로로 본다
    @Query("""
        SELECT COUNT(r) > 0
          FROM MembershipRefund r
         WHERE r.membershipPaymentAttempt.membershipPayment.membershipId IN :membershipIds
           AND r.status = :status
        """)
    boolean existsByMembershipPaymentMembershipIdInAndStatus(@Param("membershipIds") Collection<Long> membershipIds,
                                                             @Param("status") MembershipRefundStatus status);

    // TX2의 SYSTEM 환불 멱등 판정. 같은 시도의 SYSTEM 환불 행을 상태와 무관하게 모두 본다 — REQUESTED만 보면 FAILED·COMPLETED 뒤에
    // 새 요청을 또 만든다. 시도 PK 잠금 아래에서 본다(환불 생성은 모두 같은 잠금 순서 아래)
    List<MembershipRefund> findByMembershipPaymentAttemptIdAndRequestedByOrderByIdAsc(Long membershipPaymentAttemptId,
                                                                                      RefundRequester requestedBy);

    // 환불 행 PK 잠금. 범위 FOR UPDATE는 쓰지 않는다 — ID를 일반 조회로 읽은 뒤 한 행씩 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM MembershipRefund r WHERE r.id = :id")
    Optional<MembershipRefund> findByIdForUpdate(@Param("id") Long id);

    // 한 시도의 환불 행 ID 전부(PK 오름차순, 잠금 없음). 호출자가 이 순서로 한 행씩 잠근다
    @Query("SELECT r.id FROM MembershipRefund r WHERE r.membershipPaymentAttempt.id = :attemptId ORDER BY r.id ASC")
    List<Long> findIdsByMembershipPaymentAttemptIdOrderByIdAsc(@Param("attemptId") Long attemptId);

    Optional<MembershipRefund> findByIdempotencyKey(String idempotencyKey);

    // 운영 응답 조립용(잠금 없음). 시도 연관을 함께 읽어 트랜잭션 밖에서도 시도 ID를 쓸 수 있게 한다
    @Query("SELECT r FROM MembershipRefund r JOIN FETCH r.membershipPaymentAttempt WHERE r.id = :id")
    Optional<MembershipRefund> findWithAttemptById(@Param("id") Long id);

    // 운영 상세의 전체 환불 이력(MEMBER·SYSTEM, ID 순)
    List<MembershipRefund> findByMembershipPaymentAttemptIdOrderByIdAsc(Long membershipPaymentAttemptId);

    // 잠금 전 투영 조회. 회원 ID는 들어 있지 않다(이용권 ID로 회원 도메인에 묻는다)
    @Query("""
        SELECT new com.recipick.domain.payment.service.MembershipRefundRef(
            r.id, a.id, p.id, p.membershipId, a.portonePaymentId, r.status, r.requestedBy, r.amount,
            r.portoneCancellationId, r.pgRequestReason, r.leaseExpiresAt, r.nextCheckAt, r.opsReviewReason,
            r.retryOfRefundId)
          FROM MembershipRefund r
          JOIN r.membershipPaymentAttempt a
          JOIN a.membershipPayment p
         WHERE r.id = :id
        """)
    Optional<MembershipRefundRef> findRef(@Param("id") Long id);

    // 복구 스캔 대상(잠금 없음): REQUESTED이고 다음 확인 시각이 없거나 지났고 임대가 없거나 끝났고 운영 확인 표시가 없는 행.
    // 스캔 시작 시점의 최대 ID까지만 본다(스캔 중 새로 생긴 행은 다음 스캔)
    @Query("""
        SELECT r.id
          FROM MembershipRefund r
         WHERE r.status = com.recipick.domain.payment.entity.enums.MembershipRefundStatus.REQUESTED
           AND (r.nextCheckAt IS NULL OR r.nextCheckAt <= :now)
           AND (r.leaseExpiresAt IS NULL OR r.leaseExpiresAt <= :now)
           AND r.opsReviewRequiredAt IS NULL
           AND r.id > :afterId
           AND r.id <= :upperId
         ORDER BY r.id ASC
        """)
    List<Long> findRequestedForRecovery(@Param("now") LocalDateTime now, @Param("afterId") Long afterId,
                                        @Param("upperId") Long upperId, Pageable pageable);

    @Query("SELECT COALESCE(MAX(r.id), 0) FROM MembershipRefund r")
    long findMaxId();
}
