package com.recipick.domain.recipe.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "recipe_ingredient",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_recipe_ingredient_recipe_id_ingredient_id",
                columnNames = {"recipe_id", "ingredient_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    @Column(precision = 10, scale = 4)
    private BigDecimal amountPerServing;

    private boolean required;

    @Builder
    private RecipeIngredient(Recipe recipe, Ingredient ingredient, BigDecimal amountPerServing, boolean required) {
        this.recipe = recipe;
        this.ingredient = ingredient;
        this.amountPerServing = amountPerServing;
        this.required = required;
    }
}
