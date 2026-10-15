package com.recipick.domain.recipe.entity;

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
@Table(name = "ingredient")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50, unique = true)
    private String name;

    // TODO: 값 목록 확정 후 enum으로 교체
    @Column(length = 10)
    private String unit;

    @Builder
    private Ingredient(String name, String unit) {
        this.name = name;
        this.unit = unit;
    }
}
