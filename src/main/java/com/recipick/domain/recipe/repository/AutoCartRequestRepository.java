package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.AutoCartRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AutoCartRequestRepository extends JpaRepository<AutoCartRequest, Long> {

    Optional<AutoCartRequest> findByIdAndMemberId(Long id, Long memberId);
}
