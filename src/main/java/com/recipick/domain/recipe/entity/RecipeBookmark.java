package com.recipick.domain.recipe.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
// 기간별 찜 순위가 기간 조건으로 범위를 좁힌 뒤 레시피별로 세므로 created_at을 앞에 둔다
@Table(
    name = "recipe_bookmark",
    indexes = @Index(name = "idx_recipe_bookmark_created_at_recipe_id", columnList = "created_at, recipe_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeBookmark implements Persistable<RecipeBookmarkId> {

    // member는 다른 도메인이라 ID만 두고, recipe만 연관관계로 매핑한다
    @EmbeddedId
    private RecipeBookmarkId id;

    @MapsId("recipeId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private RecipeBookmark(Long memberId, Recipe recipe) {
        this.id = new RecipeBookmarkId(memberId, recipe.getId());
        this.recipe = recipe;
    }
    @Override
    public boolean isNew() {
        return createdAt == null;
    }
}
