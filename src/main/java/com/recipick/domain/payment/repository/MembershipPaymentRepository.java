package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.MembershipPayment;
import com.recipick.domain.payment.entity.enums.MembershipPaymentStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
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
}
