package com.recipick.domain.member.repository;

import com.recipick.domain.member.entity.TrialBlock;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrialBlockRepository extends JpaRepository<TrialBlock, Long> {
    boolean existsByPhoneHashAndExpiresAtAfter(String phoneHash, LocalDateTime now);

    // 신규 번호의 동시 등록도 UNIQUE와 원자적 upsert로 처리하고 기존 차단 기간을 줄이지 않는다.
    @Modifying
    @Query(value = "INSERT INTO trial_block (phone_hash, expires_at, created_at) VALUES (:hash, :expires, :created) "
            + "ON DUPLICATE KEY UPDATE expires_at = GREATEST(expires_at, :expires)", nativeQuery = true)
    void extendBlock(@Param("hash") String hash, @Param("expires") LocalDateTime expires,
            @Param("created") LocalDateTime created);

    @Modifying
    @Query(value = "DELETE FROM trial_block WHERE expires_at <= :now LIMIT 1000", nativeQuery = true)
    int deleteExpiredBatch(@Param("now") LocalDateTime now);
}
