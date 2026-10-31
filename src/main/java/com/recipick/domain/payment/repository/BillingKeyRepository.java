package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.BillingKey;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingKeyRepository extends JpaRepository<BillingKey, Long> {

    // 등록. 같은 키 행의 ID만 읽는다 — 행이 없으면 아무것도 잠그지 않는다(UNIQUE 빈 구간 잠금 없음).
    // 엔티티를 잠금 전에 올려 두면 잠금 조회가 영속성 컨텍스트의 옛 인스턴스를 돌려주므로 ID만 꺼낸다
    @Query("SELECT b.id FROM BillingKey b WHERE b.portoneBillingKey = :portoneBillingKey")
    Optional<Long> findIdByPortoneBillingKey(@Param("portoneBillingKey") String portoneBillingKey);

    // 등록. 현재 ACTIVE 행의 ID만 읽는다. 첫 등록 회원은 empty — (member_id, status) 빈 구간에 갭 잠금을 걸지 않는다
    @Query("SELECT b.id FROM BillingKey b WHERE b.memberId = :memberId AND b.status = :status")
    Optional<Long> findIdByMemberIdAndStatus(@Param("memberId") Long memberId,
                                             @Param("status") BillingKeyStatus status);

    // 존재하는 행만 PK로 잠그며 읽는다. 잠금 순서는 MEMBER → BILLING_KEY다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BillingKey b WHERE b.id = :id")
    Optional<BillingKey> findByIdForUpdate(@Param("id") Long id);

    // C-10. 회원 잠금 뒤 일반 조회
    boolean existsByMemberIdAndStatus(Long memberId, BillingKeyStatus status);

    // GET /api/billing-keys/me
    Optional<BillingKey> findByMemberIdAndStatus(Long memberId, BillingKeyStatus status);
}
