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

    // 조회수는 레시피 내용이 바뀐 게 아니라서 updated_at(마지막 수정 시각)은 갱신하지 않는다
    @Modifying
    @Query("update Recipe r set r.viewCount = r.viewCount + 1 where r.id = :id")
    int increaseViewCount(@Param("id") Long id);

    List<Recipe> findTop500ByNameChoseongIsNullAndNameIsNotNullOrderByIdAsc();

    // 초성은 이름에서 파생한 값이라 레시피 내용이 바뀐 게 아니므로 updated_at(마지막 수정 시각)은 갱신하지 않는다
    @Modifying
    @Query("update Recipe r set r.nameChoseong = :nameChoseong where r.id = :id")
    void updateNameChoseong(@Param("id") Long id, @Param("nameChoseong") String nameChoseong);
}
