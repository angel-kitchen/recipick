package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.AiRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRequestLogRepository extends JpaRepository<AiRequestLog, Long> {
}
