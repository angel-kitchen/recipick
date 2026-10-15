package com.recipick.domain.recipe.entity;

import com.recipick.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "recipe")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recipe extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String name;

    // TODO: 값 목록 확정 후 enum으로 교체
    @Column(length = 20)
    private String cuisine;

    // TODO: 값 목록 확정 후 enum으로 교체
    @Column(length = 20)
    private String dishType;

    private int baseServings;

    private Integer cookTimeMin;

    // TODO: 값 목록 확정 후 enum으로 교체
    @Column(length = 20)
    private String difficulty;

    private Integer spicyLevel;

    @Column(columnDefinition = "json")
    private String steps;

    @Column(length = 20)
    private String youtubeVideoId;

    // TODO: 값 목록 확정 후 enum으로 교체
    @Column(length = 20)
    private String status;

    private boolean autoCartEnabled;

    @Builder
    private Recipe(
            String name,
            String cuisine,
            String dishType,
            int baseServings,
            Integer cookTimeMin,
            String difficulty,
            Integer spicyLevel,
            String steps,
            String youtubeVideoId,
            String status,
            boolean autoCartEnabled
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
        this.status = status;
        this.autoCartEnabled = autoCartEnabled;
    }
}
