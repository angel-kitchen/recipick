package com.recipick.domain.recipe.dto.response;

import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.recipe.entity.AutoCartMenu;
import com.recipick.domain.recipe.entity.AutoCartRequest;
import com.recipick.domain.recipe.entity.AutoCartStatus;
import com.recipick.domain.recipe.entity.UnavailableReason;
import com.recipick.domain.recipe.service.AutoCartLine;
import com.recipick.global.common.SaleUnit;
import java.math.BigDecimal;
import java.util.List;

public record AutoCartPreviewResponse(
    Long autoCartRequestId,
    AutoCartStatus status,
    Long martId,
    String martName,
    List<Menu> menus,
    List<Item> items,
    long totalPrice,
    int purchasableCount,
    int unavailableCount
) {

    public static AutoCartPreviewResponse of(AutoCartRequest request, String martName, List<AutoCartLine> lines) {
        List<Item> items = lines.stream().map(Item::from).toList();
        int purchasableCount = (int) items.stream().filter(Item::purchasable).count();
        return new AutoCartPreviewResponse(
            request.getId(),
            request.getStatus(),
            request.getMartId(),
            martName,
            request.getMenus().stream().map(Menu::from).toList(),
            items,
            items.stream().mapToLong(Item::linePrice).sum(),
            purchasableCount,
            items.size() - purchasableCount
        );
    }

    public record Menu(Long recipeId, String name, int servings, List<Long> optionalIngredientIds) {

        static Menu from(AutoCartMenu menu) {
            return new Menu(menu.recipeId(), menu.recipeName(), menu.servings(), menu.optionalIngredientIds());
        }
    }

    public record Item(
        Long ingredientId,
        String ingredientName,
        BigDecimal totalRequiredAmount,
        SaleUnit unit,
        boolean purchasable,
        Product product,
        int purchaseQuantity,
        BigDecimal expectedRemainingAmount,
        long linePrice,
        UnavailableReason unavailableReason,
        List<Candidate> candidates
    ) {

        static Item from(AutoCartLine line) {
            AutoCartLine.Option selected = line.selected();
            return new Item(
                line.ingredient().ingredientId(),
                line.ingredient().name(),
                line.ingredient().totalRequiredAmount().stripTrailingZeros(),
                line.ingredient().unit(),
                line.purchasable(),
                selected == null ? null : Product.from(selected.product()),
                line.purchaseQuantity(),
                line.purchasable() ? selected.expectedRemainingAmount().stripTrailingZeros() : null,
                line.linePrice(),
                line.purchasable() ? null : line.unavailableReason(),
                line.candidates().stream().map(option -> Candidate.from(option.product())).toList()
            );
        }
    }

    public record Product(Long productId, String name, BigDecimal saleUnitAmount, SaleUnit unit, Long price) {

        static Product from(ProductSummary product) {
            return new Product(product.productId(), product.name(), product.saleUnitAmount().stripTrailingZeros(),
                product.saleUnit(), product.price());
        }
    }

    public record Candidate(Long productId, String name, Long price, BigDecimal saleUnitAmount) {

        static Candidate from(ProductSummary product) {
            return new Candidate(product.productId(), product.name(), product.price(),
                product.saleUnitAmount().stripTrailingZeros());
        }
    }
}
