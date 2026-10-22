package com.recipick.domain.recipe.entity;

import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.global.entity.BaseTimeEntity;
import com.recipick.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Getter
@Entity
@Table(name = "recipe")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recipe extends BaseTimeEntity {

    public static final int MIN_SERVINGS = 1;
    public static final int MAX_SERVINGS = 10;

    private static final String YOUTUBE_WATCH_URL =
        "https://www.youtube.com/watch?v=%1$s";
    private static final String YOUTUBE_THUMBNAIL_URL =
        "https://img.youtube.com/vi/%1$s/hqdefault.jpg";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private CuisineType cuisine;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private DishType dishType;

    private int baseServings;

    private Integer cookTimeMin;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private Difficulty difficulty;

    private Integer spicyLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<RecipeStep> steps;

    @Column(length = 20)
    private String youtubeVideoId;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private RecipeStatus status;

    private boolean autoCartEnabled;

    @Builder
    private Recipe(
        String name,
        CuisineType cuisine,
        DishType dishType,
        int baseServings,
        Integer cookTimeMin,
        Difficulty difficulty,
        Integer spicyLevel,
        List<RecipeStep> steps,
        String youtubeVideoId
    ) {
        this.name = name;
        this.cuisine = cuisine;
        this.dishType = dishType;
        this.baseServings = baseServings;
        this.cookTimeMin = cookTimeMin;
        this.difficulty = difficulty;
        this.spicyLevel = spicyLevel;
        this.steps = steps;
        this.youtubeVideoId = youtubeVideoId;
        this.status = RecipeStatus.ACTIVE;
        this.autoCartEnabled = false;
    }

    public void disableAutoCart() {
        this.autoCartEnabled = false;
    }

    public int resolveServings(Integer servings) {
        if (servings == null) {
            return baseServings;
        }
        if (servings < MIN_SERVINGS || servings > MAX_SERVINGS) {
            throw new BusinessException(RecipeErrorCode.INVALID_SERVINGS);
        }
        return servings;
    }

    public String getYoutubeWatchUrl() {
        return youtubeVideoId == null ? null : YOUTUBE_WATCH_URL.formatted(youtubeVideoId);
    }

    public String getThumbnailUrl() {
        return youtubeVideoId == null ? null : YOUTUBE_THUMBNAIL_URL.formatted(youtubeVideoId);
    }

    public void update(
        String name,
        CuisineType cuisine,
        DishType dishType,
        int baseServings,
        Integer cookTimeMin,
        Difficulty difficulty,
        Integer spicyLevel,
        List<RecipeStep> steps,
        String youtubeVideoId
    ) {
        this.name = name;
        this.cuisine = cuisine;
        this.dishType = dishType;
        this.baseServings = baseServings;
        this.cookTimeMin = cookTimeMin;
        this.difficulty = difficulty;
        this.spicyLevel = spicyLevel;
        this.steps = steps;
        this.youtubeVideoId = youtubeVideoId;
    }

    public void changeStatus(RecipeStatus status) {
        this.status = status;
        if (status == RecipeStatus.INACTIVE) {
            this.autoCartEnabled = false;
        }
    }

    public boolean isActive() {
        return status == RecipeStatus.ACTIVE;
    }
}
