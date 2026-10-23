package com.recipick.domain.recipe.service;

import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.domain.recipe.entity.UnavailableReason;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.global.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AutoCartCalculator {

    private static final Comparator<AutoCartLine.Option> PRIORITY = Comparator
        .comparing((AutoCartLine.Option option) -> option.unavailableReason() != null)
        .thenComparingLong(AutoCartLine.Option::linePrice)
        .thenComparing(AutoCartLine.Option::expectedRemainingAmount)
        .thenComparing(option -> option.product().name());

    private AutoCartCalculator() {
    }

    public static List<AutoCartLine> calculate(
            List<RequiredIngredient> ingredients,
            Map<Long, List<ProductSummary>> candidatesByIngredient,
            Map<Long, Integer> cartQuantities,
            Map<Long, Long> productSelections
    ) {
        // 같은 재료가 따로 들어오면 각각 올림돼 더 많이 사게 되므로, 올림 전에 재료별로 합산한다
        List<RequiredIngredient> mergedIngredients = ingredients.stream()
            .collect(Collectors.toMap(
                RequiredIngredient::ingredientId,
                ingredient -> ingredient,
                RequiredIngredient::add,
                LinkedHashMap::new
            ))
            .values().stream()
            .toList();
        Set<Long> ingredientIds = mergedIngredients.stream()
            .map(RequiredIngredient::ingredientId)
            .collect(Collectors.toSet());
        if (!ingredientIds.containsAll(productSelections.keySet())) {
            throw new BusinessException(RecipeErrorCode.INVALID_PRODUCT_SELECTION);
        }
        return mergedIngredients.stream()
            .map(ingredient -> {
                List<AutoCartLine.Option> candidates = candidatesByIngredient
                    .getOrDefault(ingredient.ingredientId(), List.of()).stream()
                    // 삭제된 상품은 판매 중·재고가 남아 있어도 다시 살 수 없으므로 후보에서 뺀다
                    .filter(product -> !product.deleted())
                    .map(product -> evaluate(product, ingredient.totalRequiredAmount(),
                            cartQuantities.getOrDefault(product.productId(), 0)))
                    .sorted(PRIORITY)
                    .toList();
                AutoCartLine.Option selected = select(candidates, productSelections.get(ingredient.ingredientId()));
                return new AutoCartLine(ingredient, selected, candidates);
            })
            .toList();
    }

    private static AutoCartLine.Option select(List<AutoCartLine.Option> candidates, Long selectedProductId) {
        if (selectedProductId == null) {
            return candidates.isEmpty() ? null : candidates.get(0);
        }
        return candidates.stream()
            .filter(option -> option.product().productId().equals(selectedProductId))
            .findFirst()
            .orElseThrow(() -> new BusinessException(RecipeErrorCode.INVALID_PRODUCT_SELECTION));
    }

    private static AutoCartLine.Option evaluate(ProductSummary product, BigDecimal requiredAmount, int cartQuantity) {
        int quantity = requiredAmount.divide(product.saleUnitAmount(), 0, RoundingMode.CEILING).intValueExact();
        BigDecimal remaining = product.saleUnitAmount()
            .multiply(BigDecimal.valueOf(quantity))
            .subtract(requiredAmount);
        return new AutoCartLine.Option(
                product,
                quantity,
            product.price() * quantity,
            remaining,
            judge(product, cartQuantity + quantity)
        );
    }

    private static UnavailableReason judge(ProductSummary product, int totalQuantity) {
        if (product.status() != ProductStatus.SELLING) {
            return UnavailableReason.SALE_STOPPED;
        }
        if (product.totalQuantity() == 0) {
            return UnavailableReason.SOLD_OUT;
        }
        if (product.totalQuantity() < totalQuantity) {
            return UnavailableReason.INSUFFICIENT_STOCK;
        }
        return null;
    }
}
