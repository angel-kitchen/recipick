package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import com.recipick.domain.payment.service.MembershipPaymentRef;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipPaymentRepository extends JpaRepository<MembershipPayment, Long> {

    // 회원 잠금 뒤 일반 조회
    boolean existsByMembershipIdInAndStatusIn(Collection<Long> membershipIds,
                                              Collection<MembershipPaymentStatus> statuses);

    // 바로 구독 TX1. 회원 잠금 뒤 일반 조회(키에 회원 ID가 붙어 있어 다른 회원 회차는 나오지 않는다)
    Optional<MembershipPayment> findByIdempotencyKey(String idempotencyKey);

    // TX2. 존재하는 행만 PK로 잠근다. 잠금 순서는 MEMBER → MEMBERSHIP → 회차 → 시도
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM MembershipPayment p WHERE p.id = :id")
    Optional<MembershipPayment> findByIdForUpdate(@Param("id") Long id);

    // 첫 결제 판정 보조. 이 이용권에 이미 PAID 회차가 있나(있으면 그 이용권은 첫 결제를 지났다)
    boolean existsByMembershipIdAndStatus(Long membershipId, MembershipPaymentStatus status);

    // TX2  이용권이 이미 활성인데 이 회차가 미반영일 때, 활성화의 근거가 된 다른 회차가 있는지.
    // 회차 PK 잠금 아래 일반 조회 — 같은 이용권의 회차를 만들거나 반영하는 경로가 모두 MEMBER 잠금 아래라 최신이다
    boolean existsByMembershipIdAndMembershipAppliedAtIsNotNullAndIdNot(Long membershipId, Long id);

    // 갱신 TX1. 회원·이용권 잠금 뒤 ID만 일반 조회한다(엔티티를 잠금 전에 올리지 않는다). 있으면 findByIdForUpdate로 다시 읽는다
    @Query("""
        SELECT p.id FROM MembershipPayment p
         WHERE p.membershipId = :membershipId AND p.periodStartDate = :periodStartDate
        """)
    Optional<Long> findIdByMembershipIdAndPeriodStartDate(@Param("membershipId") Long membershipId,
                                                          @Param("periodStartDate") LocalDate periodStartDate);

    // 같은 실행 안에서 새로 생긴 회차를 다시 집지 않도록 시작 시 상한을 고정한다(afterId < id <= upperId)
    @Query("SELECT COALESCE(MAX(p.id), 0) FROM MembershipPayment p")
    long findMaxId();

    // 재개할 수 있는 기존 갱신 회차(마감 전): 재시도 시각이 된 RETRY_WAITING, 카드 대기, 시도가 남았을 수 있는 PENDING.
    // 실제 진행 여부는 잠금 뒤 판정한다 — 이 조회가 결과 불명 시도의 새 청구를 허용하는 것이 아니다
    @Query("""
        SELECT new com.recipick.domain.payment.service.MembershipPaymentRef(
            p.id, p.membershipId, p.status, p.graceDeadlineAt, p.nextRetryAt)
          FROM MembershipPayment p
         WHERE p.graceDeadlineAt IS NOT NULL
           AND p.graceDeadlineAt > :now
           AND p.id > :afterId AND p.id <= :upperId
           AND (
               (p.status = com.recipick.domain.payment.entity.enums.MembershipPaymentStatus.RETRY_WAITING
                AND p.nextRetryAt IS NOT NULL AND p.nextRetryAt <= :now)
               OR p.status = com.recipick.domain.payment.entity.enums.MembershipPaymentStatus.PAYMENT_METHOD_REQUIRED
               OR p.status = com.recipick.domain.payment.entity.enums.MembershipPaymentStatus.PENDING
           )
         ORDER BY p.id
        """)
    List<MembershipPaymentRef> findResumableRenewals(@Param("now") LocalDateTime now, @Param("afterId") Long afterId,
                                                     @Param("upperId") Long upperId, Pageable pageable);

    // 정리 대상 갱신 회차: 마감이 지난 미해결 회차 전부 + 마감 전이라도 대기 상태(PENDING·RETRY_WAITING·카드 대기)인 회차.
    // 해지·종료된 대기 회차는 C-2 후보에서 빠지므로 여기서 찾는다. 첫 결제 회차(graceDeadlineAt null)는 제외된다
    @Query("""
        SELECT new com.recipick.domain.payment.service.MembershipPaymentRef(
            p.id, p.membershipId, p.status, p.graceDeadlineAt, p.nextRetryAt)
          FROM MembershipPayment p
         WHERE p.graceDeadlineAt IS NOT NULL
           AND p.status IN :unresolvedStatuses
           AND (p.graceDeadlineAt <= :now OR p.status IN :waitingStatuses)
           AND p.id > :afterId AND p.id <= :upperId
         ORDER BY p.id
        """)
    List<MembershipPaymentRef> findRenewalsNeedingMaintenance(
        @Param("now") LocalDateTime now, @Param("afterId") Long afterId, @Param("upperId") Long upperId,
        @Param("unresolvedStatuses") Collection<MembershipPaymentStatus> unresolvedStatuses,
        @Param("waitingStatuses") Collection<MembershipPaymentStatus> waitingStatuses, Pageable pageable);

    // 카드 등록 뒤 재개. 그 회원 이용권들의 지정 상태 갱신 회차. 호출자는 빈 ID 목록이면 부르지 않는다
    @Query("""
        SELECT new com.recipick.domain.payment.service.MembershipPaymentRef(
            p.id, p.membershipId, p.status, p.graceDeadlineAt, p.nextRetryAt)
          FROM MembershipPayment p
         WHERE p.membershipId IN :membershipIds
           AND p.status = :status
           AND p.graceDeadlineAt IS NOT NULL
         ORDER BY p.id
        """)
    List<MembershipPaymentRef> findRefsByMembershipIdInAndStatus(@Param("membershipIds") Collection<Long> membershipIds,
                                                                 @Param("status") MembershipPaymentStatus status);

    // 재시도 가드 ①. C-17 ①과 같되 현재 회차가 RETRY_WAITING·카드 대기일 때만 그 한 행을 뺀다
    @Query("""
        SELECT COUNT(p) > 0
          FROM MembershipPayment p
         WHERE p.membershipId IN :membershipIds
           AND p.status IN :statuses
           AND NOT (p.id = :excludedPaymentId
                    AND p.status IN (
                        com.recipick.domain.payment.entity.enums.MembershipPaymentStatus.RETRY_WAITING,
                        com.recipick.domain.payment.entity.enums.MembershipPaymentStatus.PAYMENT_METHOD_REQUIRED))
        """)
    boolean existsUnresolvedExceptOwnWaiting(@Param("membershipIds") Collection<Long> membershipIds,
                                             @Param("statuses") Collection<MembershipPaymentStatus> statuses,
                                             @Param("excludedPaymentId") Long excludedPaymentId);
}
