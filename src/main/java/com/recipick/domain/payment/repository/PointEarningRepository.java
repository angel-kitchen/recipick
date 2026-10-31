package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.PointEarning;
import com.recipick.domain.payment.entity.enums.PointEarningStatus;
import com.recipick.domain.payment.repository.dto.PointExpirySummary;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 여러 적립 건을 잠그는 조회는 모두 id 오름차순이다(잠금 순서).
public interface PointEarningRepository extends JpaRepository<PointEarning, Long> {

    Optional<PointEarning> findByOrderId(Long orderId);

    // 주문 ID만 아는 확정·복원·적립 확정이 MEMBER_POINT를 먼저 잠그려고 회원 ID를 값으로만 꺼낸다.
    // 적립 건 엔티티를 잠금 전에 읽어 두면 잠금을 얻은 뒤에도 영속성 컨텍스트의 예전 값을 보게 된다
    @Query("SELECT e.memberId FROM PointEarning e WHERE e.orderId = :orderId")
    Optional<Long> findMemberIdByOrderId(@Param("orderId") Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM PointEarning e WHERE e.orderId = :orderId")
    Optional<PointEarning> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    // 만료일이 지났는데 아직 만료 처리되지 않은 잔량. 포인트를 쓰기 전에 먼저 소멸시킨다(지연 만료)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM PointEarning e WHERE e.memberId = :memberId "
        + "AND e.status = com.recipick.domain.payment.entity.enums.PointEarningStatus.COMPLETED "
        + "AND e.remainingAmount > 0 AND e.expiresOn < :today ORDER BY e.id ASC")
    List<PointEarning> findExpiredForUpdate(@Param("memberId") Long memberId, @Param("today") LocalDate today);

    // 오늘 쓸 수 있는 잔량. 만료일 당일까지 사용 가능하다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM PointEarning e WHERE e.memberId = :memberId "
        + "AND e.status = com.recipick.domain.payment.entity.enums.PointEarningStatus.COMPLETED "
        + "AND e.remainingAmount > 0 AND e.expiresOn >= :today ORDER BY e.id ASC")
    List<PointEarning> findUsableForUpdate(@Param("memberId") Long memberId, @Param("today") LocalDate today);

    // 만료 배치의 대상 회원. 잠그지 않고 회원 ID만 꺼낸다 — 실제 소멸은 회원별 트랜잭션이 다시 잠그고 확인한다.
    // 처리하면서 대상이 줄어들어 OFFSET으로 넘기면 회원을 건너뛸 수 있어, 마지막으로 본 회원 ID 다음부터 읽는다
    @Query("SELECT DISTINCT e.memberId FROM PointEarning e "
        + "WHERE e.status = com.recipick.domain.payment.entity.enums.PointEarningStatus.COMPLETED "
        + "AND e.remainingAmount > 0 AND e.expiresOn < :today AND e.memberId > :afterMemberId "
        + "ORDER BY e.memberId ASC")
    List<Long> findExpirationCandidateMemberIds(@Param("today") LocalDate today,
                                                @Param("afterMemberId") Long afterMemberId, Pageable limit);

    // 오늘 실제로 쓸 수 있는 포인트. 만료 배치가 아직 처리하지 않은 만료 잔량은 날짜 조건으로 뺀다
    @Query("SELECT COALESCE(SUM(e.remainingAmount), 0) FROM PointEarning e WHERE e.memberId = :memberId "
        + "AND e.status = com.recipick.domain.payment.entity.enums.PointEarningStatus.COMPLETED "
        + "AND e.remainingAmount > 0 AND e.expiresOn >= :today")
    long sumUsableRemaining(@Param("memberId") Long memberId, @Param("today") LocalDate today);

    // 쓸 수 있는 잔량을 만료일별로 합친다. 가장 이른 날짜 한 줄만 쓰도록 호출자가 개수를 제한한다
    @Query("SELECT e.expiresOn AS expiresOn, SUM(e.remainingAmount) AS amount FROM PointEarning e "
        + "WHERE e.memberId = :memberId "
        + "AND e.status = com.recipick.domain.payment.entity.enums.PointEarningStatus.COMPLETED "
        + "AND e.remainingAmount > 0 AND e.expiresOn >= :today "
        + "GROUP BY e.expiresOn ORDER BY e.expiresOn ASC")
    List<PointExpirySummary> findUsableExpiries(@Param("memberId") Long memberId, @Param("today") LocalDate today,
                                                Pageable limit);

    // 정렬은 호출자가 넘긴 Pageable로 정한다
    Page<PointEarning> findByMemberId(Long memberId, Pageable pageable);

    // 복원할 사용 내역이 가리키는 적립 건들
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM PointEarning e WHERE e.id IN :ids ORDER BY e.id ASC")
    List<PointEarning> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);

    // 탈퇴 정산의 미정산 확인(PENDING 적립). 잠그지 않는 조회라 이 트랜잭션의 스냅숏을 보지만,
    // 늦게 보이는 PENDING은 탈퇴를 막는 쪽으로만 틀려 재시도하면 통과한다
    boolean existsByMemberIdAndStatus(Long memberId, PointEarningStatus status);

    // 탈퇴 정산의 원장 확인. 확정 적립은 MEMBER_POINT 증가와 함께만 생기므로 회원 포인트 행이 없는데 잔량이 남은 확정 적립이
    // 있으면 원장이 깨진 것이다. 만료일은 보지 않는다 — 만료 배치 전 잔량이라도 행 없이 남아 있을 수는 없다
    boolean existsByMemberIdAndStatusAndRemainingAmountGreaterThan(Long memberId, PointEarningStatus status,
                                                                   long remainingAmount);
}
