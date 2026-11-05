package com.recipick.domain.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.recipe.dto.FridgeRecipeSearchResult;
import com.recipick.domain.recipe.dto.FridgeRecipeSearchResult.FridgeRecipe;
import com.recipick.domain.recipe.dto.FridgeRecipeSearchResult.IngredientRef;
import com.recipick.domain.recipe.dto.request.RecipeSearchRequest;
import com.recipick.domain.recipe.dto.response.RecipeDetailResponse;
import com.recipick.domain.recipe.dto.response.RecipeRankingResponse;
import com.recipick.domain.recipe.dto.response.RecipeSuggestionResponse;
import com.recipick.domain.recipe.dto.response.RecipeSummaryResponse;
import com.recipick.domain.recipe.entity.Ingredient;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeBookmarkId;
import com.recipick.domain.recipe.entity.RecipeIngredient;
import com.recipick.domain.recipe.entity.RecipeStatus;
import com.recipick.domain.recipe.entity.RecipeStep;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.repository.RecipeBookmarkCount;
import com.recipick.domain.recipe.repository.IngredientRepository;
import com.recipick.domain.recipe.repository.RecipeBookmarkRepository;
import com.recipick.domain.recipe.repository.RecipeIngredientRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import com.recipick.global.common.RankingPeriod;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private RecipeRepository recipeRepository;

    @Mock
    private RecipeIngredientRepository recipeIngredientRepository;

    @Mock
    private RecipeBookmarkRepository recipeBookmarkRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    // 기준 시각을 고정해 기간 계산과 updated_at 값을 비교한다. 2026-11-03 09:00 (KST)
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    private RecipeService recipeService;

    @BeforeEach
    void setUp() {
        recipeService = new RecipeService(
            recipeRepository, recipeIngredientRepository, ingredientRepository, recipeBookmarkRepository, clock);
    }

    private Recipe recipe(Long id, String name) {
        Recipe recipe = Recipe.builder()
            .name(name)
            .baseServings(2)
            .steps(List.of(new RecipeStep(1, "멸치 육수를 냅니다.")))
            .build();
        ReflectionTestUtils.setField(recipe, "id", id);
        return recipe;
    }

    private RecipeBookmarkCount bookmarkCount(Long recipeId, long count) {
        return new RecipeBookmarkCount() {
            @Override
            public Long getRecipeId() {
                return recipeId;
            }

            @Override
            public long getBookmarkCount() {
                return count;
            }
        };
    }

    private RecipeIngredient recipeIngredient(Recipe recipe, String amountPerServing) {
        Ingredient tofu = Ingredient.builder()
            .name("두부")
            .unit(SaleUnit.G)
            .build();
        return RecipeIngredient.builder()
            .recipe(recipe)
            .ingredient(tofu)
            .amountPerServing(new BigDecimal(amountPerServing))
            .required(true)
            .build();
    }

    @Test
    @DisplayName("검색: 찜한 레시피만 bookmarked가 true다")
    void searchRecipes_bookmarked() {
        // given
        PageRequest pageable = PageRequest.of(0, 20);
        List<Recipe> recipes = List.of(recipe(12L, "된장찌개"), recipe(18L, "두부조림"));
        given(recipeRepository.search(any(), any())).willReturn(new PageImpl<>(recipes, pageable, 2));
        given(recipeBookmarkRepository.findBookmarkedRecipeIds(MEMBER_ID, List.of(12L, 18L)))
            .willReturn(Set.of(12L));

        // when
        PageResponse<RecipeSummaryResponse> response =
            recipeService.searchRecipes(MEMBER_ID, new RecipeSearchRequest(null, null, null, null), pageable);

        // then
        assertThat(response.content())
            .extracting(RecipeSummaryResponse::recipeId, RecipeSummaryResponse::bookmarked)
            .containsExactly(
                Tuple.tuple(12L, true),
                Tuple.tuple(18L, false)
            );
    }

    @Test
    @DisplayName("검색: 결과가 없으면 찜 여부를 조회하지 않는다")
    void searchRecipes_empty() {
        // given
        PageRequest pageable = PageRequest.of(0, 20);
        given(recipeRepository.search(any(), any())).willReturn(new PageImpl<>(List.of(), pageable, 0));

        // when
        PageResponse<RecipeSummaryResponse> response =
            recipeService.searchRecipes(MEMBER_ID, new RecipeSearchRequest("없는메뉴", null, null, null), pageable);

        // then
        assertThat(response.content()).isEmpty();
        verify(recipeBookmarkRepository, never()).findBookmarkedRecipeIds(any(), any());
    }

    @Test
    @DisplayName("메타정보 조회는 기본 인분만 읽고 조회수를 증가시키지 않는다")
    void metadataDoesNotIncreaseViews() {
        // given
        Recipe recipe = recipe(12L, "된장찌개");
        given(recipeRepository.findByIdAndStatus(12L, RecipeStatus.ACTIVE)).willReturn(Optional.of(recipe));

        // when
        var result = recipeService.getMetadata(12L);

        // then
        assertThat(result.baseServings()).isEqualTo(2);
        verify(recipeRepository, never()).increaseViewCount(any());
        org.mockito.Mockito.verifyNoInteractions(recipeIngredientRepository, recipeBookmarkRepository);
    }

    @Test
    @DisplayName("상세: 선택한 인분으로 재료 필요량을 환산한다")
    void getRecipe_scaled() {
        // given
        Recipe recipe = recipe(12L, "된장찌개");
        given(recipeRepository.findByIdAndStatus(12L, RecipeStatus.ACTIVE)).willReturn(Optional.of(recipe));
        given(recipeIngredientRepository.findAllWithIngredient(12L))
            .willReturn(List.of(recipeIngredient(recipe, "100.00")));
        given(recipeBookmarkRepository.existsById(new RecipeBookmarkId(MEMBER_ID, 12L))).willReturn(true);

        // when
        RecipeDetailResponse response = recipeService.getRecipe(MEMBER_ID, 12L, 3);

        // then
        assertThat(response.servings()).isEqualTo(3);
        assertThat(response.bookmarked()).isTrue();
        assertThat(response.ingredients().get(0).requiredAmount().toPlainString()).isEqualTo("300");
    }

    @Test
    @DisplayName("상세: 인분을 생략하면 기본 인분으로 환산한다")
    void getRecipe_defaultServings() {
        // given
        Recipe recipe = recipe(12L, "된장찌개");
        given(recipeRepository.findByIdAndStatus(12L, RecipeStatus.ACTIVE)).willReturn(Optional.of(recipe));
        given(recipeIngredientRepository.findAllWithIngredient(12L))
            .willReturn(List.of(recipeIngredient(recipe, "0.50")));

        // when
        RecipeDetailResponse response = recipeService.getRecipe(MEMBER_ID, 12L, null);

        // then
        assertThat(response.servings()).isEqualTo(2);
        assertThat(response.ingredients().get(0).requiredAmount().toPlainString()).isEqualTo("1");
    }

    @Test
    @DisplayName("상세: 없거나 비활성인 레시피면 RECIPE_NOT_FOUND")
    void getRecipe_notFound() {
        // given
        given(recipeRepository.findByIdAndStatus(99L, RecipeStatus.ACTIVE)).willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> recipeService.getRecipe(MEMBER_ID, 99L, null))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(RecipeErrorCode.RECIPE_NOT_FOUND);
    }

    @Test
    @DisplayName("상세: 조회만으로는 조회수를 올리지 않는다(조회수 증가는 Facade가 별도 트랜잭션으로 한다)")
    void getRecipe_doesNotIncreaseViewCount() {
        // given
        given(recipeRepository.findByIdAndStatus(12L, RecipeStatus.ACTIVE))
            .willReturn(Optional.of(recipe(12L, "된장찌개")));

        // when
        recipeService.getRecipe(MEMBER_ID, 12L, null);

        // then
        verify(recipeRepository, never()).increaseViewCount(any());
    }

    @Test
    @DisplayName("조회수: 레시피 조회수를 1 올린다")
    void increaseViewCount() {
        // when
        recipeService.increaseViewCount(12L);

        // then
        verify(recipeRepository).increaseViewCount(12L);
    }

    @Test
    @DisplayName("찜 순위: 주간은 7일 전부터 조회하고 그 기간의 찜 수와 내가 찜한 여부를 함께 내려준다")
    void findPopularRecipes_weekly() {
        // given
        PageRequest pageable = PageRequest.of(0, 20);
        LocalDateTime weekAgo = LocalDateTime.of(2026, 10, 27, 9, 0);
        List<Recipe> recipes = List.of(recipe(18L, "두부조림"), recipe(12L, "된장찌개"));
        given(recipeBookmarkRepository.findPopularRecipes(RecipeStatus.ACTIVE, weekAgo, pageable))
            .willReturn(new PageImpl<>(recipes, pageable, 2));
        given(recipeBookmarkRepository.findBookmarkedRecipeIds(MEMBER_ID, List.of(18L, 12L)))
            .willReturn(Set.of(12L));
        given(recipeBookmarkRepository.countByRecipeIdsSince(List.of(18L, 12L), weekAgo))
            .willReturn(List.of(bookmarkCount(18L, 5L), bookmarkCount(12L, 3L)));

        // when
        PageResponse<RecipeRankingResponse> response =
            recipeService.findPopularRecipes(MEMBER_ID, RankingPeriod.WEEKLY, pageable);

        // then
        assertThat(response.content())
            .extracting(RecipeRankingResponse::recipeId, RecipeRankingResponse::bookmarked,
                RecipeRankingResponse::bookmarkCount)
            .containsExactly(
                Tuple.tuple(18L, false, 5L),
                Tuple.tuple(12L, true, 3L)
            );
    }

    @Test
    @DisplayName("찜 순위: 요청한 정렬은 무시하고 페이지 정보만 넘긴다")
    void findPopularRecipes_ignoresSort() {
        // given
        PageRequest requested = PageRequest.of(1, 10, Sort.by("name"));
        PageRequest expected = PageRequest.of(1, 10);
        given(recipeBookmarkRepository.findPopularRecipes(
            RecipeStatus.ACTIVE, LocalDateTime.of(2026, 11, 2, 9, 0), expected))
            .willReturn(new PageImpl<>(List.of(), expected, 0));

        // when
        PageResponse<RecipeRankingResponse> response =
            recipeService.findPopularRecipes(MEMBER_ID, RankingPeriod.DAILY, requested);

        // then
        assertThat(response.content()).isEmpty();
        verify(recipeBookmarkRepository, never()).findBookmarkedRecipeIds(any(), any());
        verify(recipeBookmarkRepository, never()).countByRecipeIdsSince(any(), any());
    }

    @Test
    @DisplayName("조회수 순위: 조회수 순으로 조회하고 전체 기간 찜 수와 내가 찜한 여부, 조회수를 함께 내려준다")
    void findMostViewedRecipes() {
        // given
        PageRequest pageable = PageRequest.of(0, 20);
        List<Recipe> recipes = List.of(recipe(12L, "된장찌개"), recipe(18L, "두부조림"));
        ReflectionTestUtils.setField(recipes.get(0), "viewCount", 40);
        ReflectionTestUtils.setField(recipes.get(1), "viewCount", 25);
        given(recipeRepository.findByStatusOrderByViewCountDescIdDesc(RecipeStatus.ACTIVE, pageable))
            .willReturn(new PageImpl<>(recipes, pageable, 2));
        given(recipeBookmarkRepository.findBookmarkedRecipeIds(MEMBER_ID, List.of(12L, 18L)))
            .willReturn(Set.of(18L));
        given(recipeBookmarkRepository.countByRecipeIdsSince(
            List.of(12L, 18L), LocalDateTime.of(2000, 1, 1, 0, 0)))
            .willReturn(List.of(bookmarkCount(18L, 7L)));

        // when
        PageResponse<RecipeRankingResponse> response = recipeService.findMostViewedRecipes(MEMBER_ID, pageable);

        // then
        assertThat(response.content())
            .extracting(RecipeRankingResponse::recipeId, RecipeRankingResponse::bookmarked,
                RecipeRankingResponse::bookmarkCount, RecipeRankingResponse::viewCount)
            .containsExactly(
                Tuple.tuple(12L, false, 0L, 40),
                Tuple.tuple(18L, true, 7L, 25)
            );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 11})
    @DisplayName("상세: 인분이 범위를 벗어나면 INVALID_SERVINGS")
    void getRecipe_invalidServings(int servings) {
        // given
        given(recipeRepository.findByIdAndStatus(12L, RecipeStatus.ACTIVE))
            .willReturn(Optional.of(recipe(12L, "된장찌개")));

        // when, then
        assertThatThrownBy(() -> recipeService.getRecipe(MEMBER_ID, 12L, servings))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(RecipeErrorCode.INVALID_SERVINGS);
    }

    @Test
    @DisplayName("냉장고 파먹기: 띄어쓰기만 다른 이름도 같은 재료로 보고, 가진 재료를 많이 쓰는 레시피부터 돌려준다")
    void findRecipesByOwnedIngredients_sortsByOwnedIngredients() {
        // given
        Ingredient kimchi = ingredient(1L, "김치");
        Ingredient tofu = ingredient(2L, "두부");
        Ingredient pork = ingredient(3L, "돼지고기 앞다리살");
        Ingredient egg = ingredient(4L, "계란");
        Ingredient salt = ingredient(5L, "소금");
        Recipe kimchiStew = recipe(10L, "김치찌개");
        Recipe tofuStew = recipe(11L, "두부조림");
        Recipe eggRoll = recipe(12L, "계란말이");
        given(ingredientRepository.findAll()).willReturn(List.of(kimchi, tofu, pork, egg, salt));
        given(recipeRepository.findAllByStatusOrderByIdAsc(RecipeStatus.ACTIVE))
            .willReturn(List.of(kimchiStew, tofuStew, eggRoll));
        given(recipeIngredientRepository.findAllWithIngredientByRecipeIds(List.of(10L, 11L, 12L))).willReturn(List.of(
            recipeIngredient(kimchiStew, kimchi, true),
            recipeIngredient(kimchiStew, tofu, true),
            recipeIngredient(kimchiStew, pork, true),
            recipeIngredient(tofuStew, tofu, true),
            recipeIngredient(tofuStew, salt, false),
            recipeIngredient(eggRoll, egg, true)));

        // when
        FridgeRecipeSearchResult result =
            recipeService.findRecipesByOwnedIngredients(List.of("김치", "두 부", "용과", " "));

        // then
        assertThat(result.unknownIngredientNames()).containsExactly("용과");
        assertThat(result.recipes()).extracting(FridgeRecipe::name).containsExactly("김치찌개", "두부조림");
        FridgeRecipe stew = result.recipes().get(0);
        assertThat(stew.ownedIngredients()).extracting(IngredientRef::name).containsExactly("김치", "두부");
        assertThat(stew.missingIngredients()).containsExactly(new IngredientRef(3L, "돼지고기 앞다리살"));
        assertThat(result.recipes().get(1).missingIngredients()).isEmpty();
    }

    @Test
    @DisplayName("냉장고 파먹기: 아는 재료가 하나도 없으면 레시피를 조회하지 않고 모르는 재료만 돌려준다")
    void findRecipesByOwnedIngredients_returnsOnlyUnknownNamesWhenNoKnownIngredient() {
        // given
        given(ingredientRepository.findAll()).willReturn(List.of(ingredient(1L, "김치")));

        // when
        FridgeRecipeSearchResult result = recipeService.findRecipesByOwnedIngredients(List.of("용과"));

        // then
        assertThat(result.unknownIngredientNames()).containsExactly("용과");
        assertThat(result.recipes()).isEmpty();
        verify(recipeIngredientRepository, never()).findAllWithIngredientByRecipeIds(anyCollection());
    }

    @Test
    @DisplayName("냉장고 파먹기: 겹치는 레시피가 많아도 10개까지만 돌려준다")
    void findRecipesByOwnedIngredients_limitsToTenRecipes() {
        // given
        Ingredient egg = ingredient(4L, "계란");
        List<Recipe> recipes = LongStream.rangeClosed(1, 12)
            .mapToObj(id -> recipe(id, "계란 요리 " + id))
            .toList();
        given(ingredientRepository.findAll()).willReturn(List.of(egg));
        given(recipeRepository.findAllByStatusOrderByIdAsc(RecipeStatus.ACTIVE)).willReturn(recipes);
        given(recipeIngredientRepository.findAllWithIngredientByRecipeIds(anyCollection()))
            .willReturn(recipes.stream().map(recipe -> recipeIngredient(recipe, egg, true)).toList());

        // when
        FridgeRecipeSearchResult result = recipeService.findRecipesByOwnedIngredients(List.of("계란"));

        // then
        assertThat(result.recipes()).hasSize(10)
            .extracting(FridgeRecipe::recipeId).startsWith(1L, 2L);
    }

    @Test
    @DisplayName("자동완성은 이름이 검색어로 시작하는 레시피를 먼저, 같은 순위는 조회수 많은 순으로 띄어쓰기를 무시하고 보여 준다")
    void findSuggestions_ordersPrefixMatchesFirstThenViewCount() {
        // given
        given(recipeRepository.findAllByStatusOrderByIdAsc(RecipeStatus.ACTIVE)).willReturn(List.of(
            recipeWithViews(1L, "참치김치찌개", 500),
            recipeWithViews(2L, "김치볶음밥", 10),
            recipeWithViews(3L, "김치찌개", 300),
            recipeWithViews(4L, "된장찌개", 900)));

        // when
        List<RecipeSuggestionResponse> suggestions = recipeService.findSuggestions("김치 ");

        // then
        assertThat(suggestions).extracting(RecipeSuggestionResponse::name)
            .containsExactly("김치찌개", "김치볶음밥", "참치김치찌개");
    }

    @Test
    @DisplayName("초성만 입력하면 레시피 이름의 초성과 비교한다")
    void findSuggestions_matchesChoseong() {
        // given
        given(recipeRepository.findAllByStatusOrderByIdAsc(RecipeStatus.ACTIVE)).willReturn(List.of(
            recipeWithViews(1L, "김치찌개", 0),
            recipeWithViews(2L, "까르보나라", 0),
            recipeWithViews(3L, "된장찌개", 0)));

        // when
        List<RecipeSuggestionResponse> suggestions = recipeService.findSuggestions("ㄱㅊ");

        // then
        assertThat(suggestions).extracting(RecipeSuggestionResponse::name).containsExactly("김치찌개");
    }

    @Test
    @DisplayName("자동완성은 최대 8개까지 보여 주고, 영문은 대소문자를 가리지 않는다")
    void findSuggestions_limitsToEightIgnoringCase() {
        // given
        List<Recipe> recipes = LongStream.rangeClosed(1, 10)
            .mapToObj(id -> recipeWithViews(id, "Pasta " + id, (int) id))
            .toList();
        given(recipeRepository.findAllByStatusOrderByIdAsc(RecipeStatus.ACTIVE)).willReturn(recipes);

        // when
        List<RecipeSuggestionResponse> suggestions = recipeService.findSuggestions("pas");

        // then
        assertThat(suggestions).hasSize(8).extracting(RecipeSuggestionResponse::recipeId).startsWith(10L, 9L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("검색어가 비었으면 레시피를 조회하지 않고 빈 목록을 준다")
    void findSuggestions_returnsEmptyForBlankKeyword(String keyword) {
        // when
        List<RecipeSuggestionResponse> suggestions = recipeService.findSuggestions(keyword);

        // then
        assertThat(suggestions).isEmpty();
        verify(recipeRepository, never()).findAllByStatusOrderByIdAsc(any());
    }

    private Recipe recipeWithViews(Long id, String name, int viewCount) {
        Recipe recipe = recipe(id, name);
        ReflectionTestUtils.setField(recipe, "viewCount", viewCount);
        return recipe;
    }

    private Ingredient ingredient(Long id, String name) {
        Ingredient ingredient = Ingredient.builder()
            .name(name)
            .unit(SaleUnit.G)
            .build();
        ReflectionTestUtils.setField(ingredient, "id", id);
        return ingredient;
    }

    private RecipeIngredient recipeIngredient(Recipe recipe, Ingredient ingredient, boolean required) {
        return RecipeIngredient.builder()
            .recipe(recipe)
            .ingredient(ingredient)
            .amountPerServing(BigDecimal.TEN)
            .required(required)
            .build();
    }
}
