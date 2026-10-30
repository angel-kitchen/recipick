package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.MemberPoint;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberPointRepository extends JpaRepository<MemberPoint, Long> {

    // 처음 포인트를 쓰는 회원은 잠글 행이 없다. member_id UNIQUE로 행만 보장하고 이미 있으면 아무 컬럼도 바꾸지 않는다 —
    // 동시에 들어와도 한 행만 생기고 기존 잔액을 0으로 덮지 않는다. 네이티브 INSERT라 시각 컬럼을 직접 넣는다
    @Modifying
    @Query(value = "INSERT INTO member_point (member_id, balance, created_at, updated_at) "
        + "VALUES (:memberId, 0, :now, :now) ON DUPLICATE KEY UPDATE member_id = member_id", nativeQuery = true)
    void insertIfAbsent(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);

    // 잠그지 않는 존재 확인. 행이 있는 회원에게 INSERT를 시도하지 않으려고 먼저 본다
    boolean existsByMemberId(Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT mp FROM MemberPoint mp WHERE mp.memberId = :memberId")
    Optional<MemberPoint> findByMemberIdForUpdate(@Param("memberId") Long memberId);
}
