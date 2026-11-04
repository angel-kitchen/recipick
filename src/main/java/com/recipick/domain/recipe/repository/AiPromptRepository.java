package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.AiPrompt;
import com.recipick.domain.recipe.entity.AiPromptType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiPromptRepository extends JpaRepository<AiPrompt, Long> {

    Optional<AiPrompt> findByPromptTypeAndActiveTrue(AiPromptType promptType);

    List<AiPrompt> findAllByPromptTypeOrderByVersionDesc(AiPromptType promptType);

    // 같은 종류의 버전 번호 계산과 활성 변경이 겹치지 않도록 그 종류의 행을 모두 잠근다.
    // 행이 없을 때는 MySQL이 (prompt_type, version) 인덱스 범위를 잠그고, 그래도 겹치면 UNIQUE 제약이 막는다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM AiPrompt p WHERE p.promptType = :promptType ORDER BY p.id")
    List<AiPrompt> findAllByPromptTypeForUpdate(@Param("promptType") AiPromptType promptType);
}
