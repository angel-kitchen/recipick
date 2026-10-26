package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.AutoCartRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AutoCartRequestRepository extends JpaRepository<AutoCartRequest, Long> {

    Optional<AutoCartRequest> findByIdAndMemberId(Long id, Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM AutoCartRequest r WHERE r.id = :id AND r.memberId = :memberId")
    Optional<AutoCartRequest> findByIdAndMemberIdForUpdate(@Param("id") Long id, @Param("memberId") Long memberId);
}
