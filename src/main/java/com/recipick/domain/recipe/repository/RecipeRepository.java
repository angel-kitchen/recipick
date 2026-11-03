package com.recipick.domain.recipe.repository;

import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long>, RecipeRepositoryCustom {

    Optional<Recipe> findByIdAndStatus(Long id, RecipeStatus status);

    List<Recipe> findAllByStatusAndAutoCartEnabledTrueOrderByIdAsc(RecipeStatus status);

    List<Recipe> findAllByStatusOrderByIdAsc(RecipeStatus status);

    Page<Recipe> findByStatusOrderByViewCountDescIdDesc(RecipeStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Recipe r WHERE r.id = :id")
    Optional<Recipe> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("update Recipe r set r.viewCount = r.viewCount + 1, r.updatedAt = :now where r.id = :id")
    int increaseViewCount(@Param("id") Long id, @Param("now") LocalDateTime now);
}
