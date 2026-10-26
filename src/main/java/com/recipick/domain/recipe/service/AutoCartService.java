package com.recipick.domain.recipe.service;

import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.recipe.dto.request.AutoCartPreviewRequest;
import com.recipick.domain.recipe.entity.AutoCartItem;
import com.recipick.domain.recipe.entity.AutoCartMenu;
import com.recipick.domain.recipe.entity.AutoCartRequest;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeIngredient;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.repository.AutoCartItemRepository;
import com.recipick.domain.recipe.repository.AutoCartRequestRepository;
import com.recipick.domain.recipe.repository.IngredientRepository;
import com.recipick.domain.recipe.repository.RecipeIngredientRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AutoCartService {

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final AutoCartRequestRepository autoCartRequestRepository;
    private final AutoCartItemRepository autoCartItemRepository;
    private final IngredientRepository ingredientRepository;

    public List<AutoCartMenu> resolveMenus(List<AutoCartPreviewRequest.Menu> menus) {
        List<Long> recipeIds = menus.stream()
            .map(AutoCartPreviewRequest.Menu::recipeId)
            .toList();
        if (recipeIds.size() != recipeIds.stream().distinct().count()) {
            throw new BusinessException(RecipeErrorCode.DUPLICATE_MENU_IN_REQUEST);
        }
        Map<Long, Recipe> recipes = recipeRepository.findAllById(recipeIds).stream()
            .collect(Collectors.toMap(Recipe::getId, Function.identity()));
        Map<Long, Set<Long>> optionalIds = recipeIngredientRepository.findAllWithIngredientByRecipeIds(recipeIds)
            .stream()
            .filter(recipeIngredient -> !recipeIngredient.isRequired())
            .collect(Collectors.groupingBy(
                recipeIngredient -> recipeIngredient.getRecipe().getId(),
                Collectors.mapping(recipeIngredient -> recipeIngredient.getIngredient().getId(), Collectors.toSet())
            ));

        return menus.stream()
            .map(menu -> toMenu(menu, recipes.get(menu.recipeId()),
                optionalIds.getOrDefault(menu.recipeId(), Set.of())))
            .toList();
    }

    public List<RequiredIngredient> sumIngredients(List<AutoCartMenu> menus) {
        Map<Long, AutoCartMenu> menuByRecipeId = menus.stream()
            .collect(Collectors.toMap(AutoCartMenu::recipeId, Function.identity()));
        Map<Long, RequiredIngredient> totals = new LinkedHashMap<>();
        for (RecipeIngredient recipeIngredient : recipeIngredientRepository.findAllWithIngredientByRecipeIds(
            menuByRecipeId.keySet())) {
            AutoCartMenu menu = menuByRecipeId.get(recipeIngredient.getRecipe().getId());
            Long ingredientId = recipeIngredient.getIngredient().getId();
            if (!recipeIngredient.isRequired() && !menu.optionalIngredientIds().contains(ingredientId)) {
                continue;
            }
            totals.merge(ingredientId, new RequiredIngredient(
                ingredientId,
                recipeIngredient.getIngredient().getName(),
                recipeIngredient.getUnit(),
                recipeIngredient.calculateRequiredAmount(menu.servings())
            ), RequiredIngredient::add);
        }
        return List.copyOf(totals.values());
    }

    @Transactional
    public AutoCartRequest savePreview(Long memberId, Long martId, List<AutoCartMenu> menus, long totalPrice) {
        return autoCartRequestRepository.save(AutoCartRequest.builder()
            .memberId(memberId)
            .martId(martId)
            .menus(menus)
            .totalPrice(totalPrice)
            .build());
    }

    public AutoCartRequest getRequest(Long memberId, Long autoCartRequestId) {
        return autoCartRequestRepository.findByIdAndMemberId(autoCartRequestId, memberId)
            .orElseThrow(() -> new BusinessException(RecipeErrorCode.AUTO_CART_REQUEST_NOT_FOUND));
    }

    // 클래스 기본값이 readOnly라, 잠금 조회(SELECT ... FOR UPDATE)는 쓰기 트랜잭션으로 명시한다
    @Transactional
    public AutoCartRequest getRequestForUpdate(Long memberId, Long autoCartRequestId) {
        return autoCartRequestRepository.findByIdAndMemberIdForUpdate(autoCartRequestId, memberId)
            .orElseThrow(() -> new BusinessException(RecipeErrorCode.AUTO_CART_REQUEST_NOT_FOUND));
    }

    // 구매 불가 재료도 함께 저장해야 기록에서 "무엇을 왜 못 담았는지"를 다시 보여줄 수 있다
    @Transactional
    public void confirm(AutoCartRequest request, List<AutoCartLine> lines, long totalPrice, LocalDateTime now) {
        request.confirm(totalPrice, now);
        autoCartItemRepository.saveAll(lines.stream()
            .map(line -> toItem(request, line))
            .toList());
    }

    public Page<AutoCartRequest> findRequests(Long memberId, Pageable pageable) {
        Pageable latestFirst = PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );
        return autoCartRequestRepository.findByMemberId(memberId, latestFirst);
    }

    public List<AutoCartItem> findItems(Long autoCartRequestId) {
        return autoCartItemRepository.findAllWithIngredient(autoCartRequestId);
    }

    private AutoCartItem toItem(AutoCartRequest request, AutoCartLine line) {
        ProductSummary product = line.selected() == null ? null : line.selected().product();
        return AutoCartItem.builder()
            .autoCartRequest(request)
            .ingredient(ingredientRepository.getReferenceById(line.ingredient().ingredientId()))
            .productId(product == null ? null : product.productId())
            .productName(product == null ? null : product.name())
            .saleUnitAmount(product == null ? null : product.saleUnitAmount())
            .unitPrice(product == null ? null : product.price())
            .requiredAmount(line.ingredient().totalRequiredAmount())
            .purchaseQuantity(line.purchaseQuantity())
            .linePrice(line.linePrice())
            .unavailableReason(line.purchasable() ? null : line.unavailableReason())
            .build();
    }

    private AutoCartMenu toMenu(AutoCartPreviewRequest.Menu menu, Recipe recipe, Set<Long> optionalIds) {
        if (recipe == null) {
            throw new BusinessException(RecipeErrorCode.RECIPE_NOT_FOUND);
        }
        if (!recipe.isActive() || !recipe.isAutoCartEnabled()) {
            throw new BusinessException(RecipeErrorCode.RECIPE_NOT_AUTO_CART_ENABLED);
        }
        int servings = recipe.resolveServings(menu.servings());
        List<Long> selectedOptionalIds = menu.optionalIngredientIds() == null
            ? List.of()
            : menu.optionalIngredientIds().stream().distinct().toList();
        if (!optionalIds.containsAll(selectedOptionalIds)) {
            throw new BusinessException(RecipeErrorCode.OPTIONAL_INGREDIENT_NOT_IN_RECIPE);
        }
        return new AutoCartMenu(recipe.getId(), recipe.getName(), servings, selectedOptionalIds);
    }
}
