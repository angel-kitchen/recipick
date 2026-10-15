package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.AutoCartRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutoCartRequestRepository extends JpaRepository<AutoCartRequest, Long> {
}
