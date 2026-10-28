package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.PointEarning;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 여러 적립 건을 잠그는 조회는 모두 id 오름차순이다(잠금 순서). 어떤 포인트부터 쓰는지(FEFO: 만료일 → id)는
// 잠금 순서와 다른 업무 규칙이라 서비스가 잠근 뒤 따로 정렬한다 — 이 조회 결과를 사용 순서로 쓰면 안 된다
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

    // 복원할 사용 내역이 가리키는 적립 건들
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM PointEarning e WHERE e.id IN :ids ORDER BY e.id ASC")
    List<PointEarning> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);
}
