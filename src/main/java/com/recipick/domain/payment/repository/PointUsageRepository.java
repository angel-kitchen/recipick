package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.PointUsage;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointUsageRepository extends JpaRepository<PointUsage, Long> {

    List<PointUsage> findAllByOrderIdOrderByIdAsc(Long orderId);

    // 최신 상태를 읽어야 이미 복원·확정된 사용을 다시 처리하지 않는다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM PointUsage u WHERE u.orderId = :orderId ORDER BY u.id ASC")
    List<PointUsage> findAllByOrderIdForUpdate(@Param("orderId") Long orderId);

    // 탈퇴 정산의 미정산 확인(RESERVED 사용). 사용 내역은 회원 ID를 직접 갖지 않아 적립 건을 거쳐 본다.
    // 잠그지 않는 조회라 스냅숏을 보지만, 늦게 보이는 RESERVED는 탈퇴를 막는 쪽으로만 틀린다
    @Query("SELECT COUNT(u) > 0 FROM PointUsage u WHERE u.pointEarning.memberId = :memberId "
        + "AND u.status = com.recipick.domain.payment.entity.enums.PointUsageStatus.RESERVED")
    boolean existsReservedByMemberId(@Param("memberId") Long memberId);
}
