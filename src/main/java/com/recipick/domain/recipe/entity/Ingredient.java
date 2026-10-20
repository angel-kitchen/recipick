package com.recipick.domain.recipe.entity;

import com.recipick.global.common.SaleUnit;
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

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(10)")
    private SaleUnit unit;

    @Builder
    private Ingredient(String name, SaleUnit unit) {
        this.name = name;
        this.unit = unit;
    }
}
