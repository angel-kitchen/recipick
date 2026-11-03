package com.recipick.domain.recipe.service;

import com.recipick.domain.recipe.dto.request.RecipeSearchRequest;
import com.recipick.domain.recipe.dto.response.RecipeDetailResponse;
import com.recipick.domain.recipe.dto.response.RecipeRankingResponse;
import com.recipick.domain.recipe.dto.response.RecipeSummaryResponse;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeBookmarkId;
import com.recipick.domain.recipe.entity.RecipeIngredient;
import com.recipick.global.common.RankingPeriod;
import com.recipick.domain.recipe.entity.RecipeStatus;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.repository.RecipeBookmarkCount;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
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
