package com.recipick.domain.recipe.service;

import com.recipick.domain.recipe.dto.FridgeRecipeSearchResult;
import com.recipick.domain.recipe.dto.FridgeRecipeSearchResult.FridgeRecipe;
import com.recipick.domain.recipe.dto.FridgeRecipeSearchResult.IngredientRef;
import com.recipick.domain.recipe.dto.request.RecipeSearchRequest;
import com.recipick.domain.recipe.dto.response.RecipeDetailResponse;
import com.recipick.domain.recipe.dto.response.RecipeRankingResponse;
import com.recipick.domain.recipe.dto.response.RecipeSummaryResponse;
import com.recipick.domain.recipe.entity.Ingredient;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeBookmarkId;
import com.recipick.domain.recipe.entity.RecipeIngredient;
import com.recipick.global.common.RankingPeriod;
import com.recipick.domain.recipe.entity.RecipeStatus;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.repository.RecipeBookmarkCount;
import com.recipick.domain.recipe.repository.IngredientRepository;
import com.recipick.domain.recipe.repository.RecipeBookmarkRepository;
import com.recipick.domain.recipe.repository.RecipeIngredientRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.domain.recipe.repository.RecipeSearchCondition;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeService {

    // AI가 한 번에 읽고 고를 만큼만 돌려준다. 추천은 이 중 최대 3개다
    private static final int FRIDGE_RECIPE_LIMIT = 10;

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final IngredientRepository ingredientRepository;
    private final RecipeBookmarkRepository recipeBookmarkRepository;
    private final Clock clock;

    public PageResponse<RecipeSummaryResponse> searchRecipes(
            Long memberId,
            RecipeSearchRequest request,
            Pageable pageable
    ) {
        RecipeSearchCondition condition = new RecipeSearchCondition(
                request.keyword(),
                request.cuisineType(),
                request.dishType(),
                request.autoCartAvailable(),
                RecipeStatus.ACTIVE
        );
        Page<Recipe> recipes = recipeRepository.search(condition, pageable);
        Set<Long> bookmarkedRecipeIds = findBookmarkedRecipeIds(memberId, recipes.getContent());

        return PageResponse.from(recipes.map(recipe ->
            RecipeSummaryResponse.of(recipe, bookmarkedRecipeIds.contains(recipe.getId()))));
    }

    public PageResponse<RecipeRankingResponse> findPopularRecipes(
        Long memberId,
        RankingPeriod period,
        Pageable pageable
    ){
        LocalDateTime from = period.startFrom(LocalDateTime.now(clock));
        Page<Recipe> recipes = recipeBookmarkRepository.findPopularRecipes(RecipeStatus.ACTIVE, from, withoutSort(pageable));
        return toRankingPage(memberId, recipes, from);
    }

    public RecipeDetailResponse getRecipe(Long memberId, Long recipeId, Integer servings) {
        Recipe recipe = getActiveRecipe(recipeId);
        int resolvedServings = recipe.resolveServings(servings);
        List<RecipeIngredient> recipeIngredients =
                recipeIngredientRepository.findAllWithIngredient(recipeId);
        boolean bookmarked = recipeBookmarkRepository.existsById(new RecipeBookmarkId(memberId, recipeId));

        return RecipeDetailResponse.of(recipe, resolvedServings, recipeIngredients, bookmarked);
    }

    // 상세 조회와 다른 트랜잭션으로 둬서 UPDATE의 행 락을 이 한 문장 동안만 잡는다
    @Transactional
    public void increaseViewCount(Long recipeId) {
        recipeRepository.increaseViewCount(recipeId);
    }

    // 조회수 순위의 찜 수는 기간 없이 전체 기간 기준이다
    public PageResponse<RecipeRankingResponse> findMostViewedRecipes(Long memberId, Pageable pageable){
        Page<Recipe> recipes = recipeRepository
            .findByStatusOrderByViewCountDescIdDesc(RecipeStatus.ACTIVE, withoutSort(pageable));
        LocalDateTime from = RankingPeriod.ALL.startFrom(LocalDateTime.now(clock));
        return toRankingPage(memberId, recipes, from);
    }

    private PageResponse<RecipeRankingResponse> toRankingPage(Long memberId, Page<Recipe> recipes, LocalDateTime from) {
        Set<Long> bookmarkedRecipeIds = findBookmarkedRecipeIds(memberId, recipes.getContent());
        Map<Long, Long> bookmarkCounts = findBookmarkCounts(recipes.getContent(), from);

        return PageResponse.from(recipes.map(recipe -> RecipeRankingResponse.of(
            recipe,
            bookmarkedRecipeIds.contains(recipe.getId()),
            bookmarkCounts.getOrDefault(recipe.getId(), 0L))));
    }

    private Map<Long, Long> findBookmarkCounts(List<Recipe> recipes, LocalDateTime from) {
        if (recipes.isEmpty()) {
            return Map.of();
        }
        List<Long> recipeIds = recipes.stream()
            .map(Recipe::getId)
            .toList();
        return recipeBookmarkRepository.countByRecipeIdsSince(recipeIds, from).stream()
            .collect(Collectors.toMap(RecipeBookmarkCount::getRecipeId, RecipeBookmarkCount::getBookmarkCount));
    }

    // 순위 API는 순서가 정해져 있으므로 클라이언트가 보낸 sort는 무시하고 page·size만 쓴다
    private static Pageable withoutSort(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    private Recipe getActiveRecipe(Long recipeId) {
        return recipeRepository.findByIdAndStatus(recipeId, RecipeStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException(RecipeErrorCode.RECIPE_NOT_FOUND));
    }

    // 등록 레시피가 100개 남짓이라 활성 레시피를 한 번에 읽고 조건은 메모리에서 거른다. 순서는 ID순으로 고정한다
    public List<Recipe> findRecommendCandidates(RecipeRecommendCondition condition) {
        Set<Long> excludedRecipeIds = condition.excludedIngredients().stream()
            .flatMap(name -> recipeIngredientRepository
                .findRecipeIdsByIngredientNameContaining(escapeLikePattern(name))
                .stream())
            .collect(Collectors.toSet());
        return recipeRepository.findAllByStatusOrderByIdAsc(RecipeStatus.ACTIVE).stream()
            .filter(condition::matches)
            .filter(recipe -> !excludedRecipeIds.contains(recipe.getId()))
            .toList();
    }

    // 냉장고 파먹기에서 AI가 가진 재료를 등록 이름으로 옮기도록 넘기는 목록. 프롬프트 캐시가 맞도록 순서를 ID순으로 고정한다
    public List<String> findIngredientNames() {
        return ingredientRepository.findAll(Sort.by("id")).stream()
            .map(Ingredient::getName)
            .toList();
    }

    // 냉장고 파먹기 레시피 찾기 Tool. 띄어쓰기만 다른 이름은 같은 재료로 보고, 등록되지 않은 이름은 AI가 안내하도록 돌려준다.
    // 양념까지 따지면 거의 모든 레시피가 부족해 보여 필수 재료로만 비교하고, 가진 재료와 하나도 겹치지 않는 레시피는 뺀다
    public FridgeRecipeSearchResult findRecipesByOwnedIngredients(List<String> ingredientNames) {
        Map<String, Ingredient> ingredientsByName = ingredientRepository.findAll().stream()
            .collect(Collectors.toMap(ingredient -> withoutSpaces(ingredient.getName()), Function.identity(),
                (first, second) -> first));
        Set<Long> ownedIngredientIds = new HashSet<>();
        List<String> unknownNames = new ArrayList<>();
        for (String name : ingredientNames.stream().filter(StringUtils::hasText).distinct().toList()) {
            Ingredient ingredient = ingredientsByName.get(withoutSpaces(name));
            if (ingredient == null) {
                unknownNames.add(name);
            } else {
                ownedIngredientIds.add(ingredient.getId());
            }
        }
        if (ownedIngredientIds.isEmpty()) {
            return new FridgeRecipeSearchResult(unknownNames, List.of());
        }

        List<Recipe> recipes = recipeRepository.findAllByStatusOrderByIdAsc(RecipeStatus.ACTIVE);
        Map<Long, List<RecipeIngredient>> requiredByRecipeId = recipeIngredientRepository
            .findAllWithIngredientByRecipeIds(recipes.stream().map(Recipe::getId).toList()).stream()
            .filter(RecipeIngredient::isRequired)
            .collect(Collectors.groupingBy(ri -> ri.getRecipe().getId()));
        // 부족한 재료 수만 보면 가진 재료를 하나만 쓰는 간단한 레시피가 앞서므로(감자·양파·당근 → 김치전),
        // 가진 재료를 많이 쓰는 레시피를 먼저 둔다. 정렬이 안정적이라 둘 다 같으면 ID순이 유지된다
        List<FridgeRecipe> matched = recipes.stream()
            .map(recipe -> toFridgeRecipe(recipe, requiredByRecipeId.getOrDefault(recipe.getId(), List.of()),
                ownedIngredientIds))
            .filter(recipe -> !recipe.ownedIngredients().isEmpty())
            .sorted(Comparator.comparing((FridgeRecipe recipe) -> recipe.ownedIngredients().size(),
                    Comparator.reverseOrder())
                .thenComparingInt(recipe -> recipe.missingIngredients().size()))
            .limit(FRIDGE_RECIPE_LIMIT)
            .toList();
        return new FridgeRecipeSearchResult(unknownNames, matched);
    }

    private FridgeRecipe toFridgeRecipe(Recipe recipe, List<RecipeIngredient> required, Set<Long> ownedIngredientIds) {
        Map<Boolean, List<IngredientRef>> byOwned = required.stream()
            .collect(Collectors.partitioningBy(ri -> ownedIngredientIds.contains(ri.getIngredient().getId()),
                Collectors.mapping(ri -> new IngredientRef(ri.getIngredient().getId(), ri.getIngredient().getName()),
                    Collectors.toList())));
        return new FridgeRecipe(recipe.getId(), recipe.getName(), byOwned.get(true), byOwned.get(false));
    }

    private String withoutSpaces(String name) {
        return name.replaceAll("\\s+", "");
    }

    // 조건 추천 ③에서 재료 이름만 쓴 문장도 고를 수 있게 넘기는 재료. 양념까지 다 넘기면 토큰만 늘어 필수 재료만 준다
    public Map<Long, List<String>> findRequiredIngredientNames(List<Long> recipeIds) {
        return recipeIngredientRepository.findAllWithIngredientByRecipeIds(recipeIds).stream()
            .filter(RecipeIngredient::isRequired)
            .collect(Collectors.groupingBy(
                recipeIngredient -> recipeIngredient.getRecipe().getId(),
                Collectors.mapping(recipeIngredient -> recipeIngredient.getIngredient().getName(),
                    Collectors.toList())));
    }

    // AI가 준 재료 이름의 %·_가 와일드카드로 해석되면 그 재료가 없는 레시피까지 빠지므로 글자 그대로 찾게 한다
    private static String escapeLikePattern(String name) {
        return name.replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
    }

    private Set<Long> findBookmarkedRecipeIds(Long memberId, List<Recipe> recipes) {
        if (recipes.isEmpty()) {
            return Set.of();
        }
        List<Long> recipeIds = recipes.stream()
            .map(Recipe::getId)
            .toList();
        return recipeBookmarkRepository.findBookmarkedRecipeIds(memberId, recipeIds);
    }
}
