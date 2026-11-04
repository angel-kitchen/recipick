package com.recipick.domain.recipe.controller;

import com.recipick.domain.recipe.dto.request.AiFridgeRecommendRequest;
import com.recipick.domain.recipe.dto.request.AiRecipeRecommendRequest;
import com.recipick.domain.recipe.dto.request.RecipeSearchRequest;
import com.recipick.domain.recipe.dto.response.AiFridgeRecommendResponse;
import com.recipick.domain.recipe.dto.response.AiRecipeRecommendResponse;
import com.recipick.domain.recipe.dto.response.RecipeDetailResponse;
import com.recipick.domain.recipe.dto.response.RecipeMetadataResponse;
import com.recipick.domain.recipe.dto.response.RecipeRankingResponse;
import com.recipick.domain.recipe.dto.response.RecipeSummaryResponse;
import com.recipick.domain.recipe.facade.AiFridgeRecommendFacade;
import com.recipick.domain.recipe.facade.AiRecipeRecommendFacade;
import com.recipick.domain.recipe.facade.RecipeFacade;
import com.recipick.domain.recipe.service.RecipeService;
import com.recipick.global.common.RankingPeriod;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.security.LoginMember;
import com.recipick.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;
    private final RecipeFacade recipeFacade;
    private final AiRecipeRecommendFacade aiRecipeRecommendFacade;
    private final AiFridgeRecommendFacade aiFridgeRecommendFacade;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RecipeSummaryResponse>>> searchRecipes(
            @LoginMember Long memberId,
            @Valid @ModelAttribute RecipeSearchRequest request,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(recipeService.searchRecipes(memberId, request, pageable)));
    }

    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<PageResponse<RecipeRankingResponse>>> findPopularRecipes(
        @LoginMember Long memberId,
        @RequestParam(defaultValue = "WEEKLY") RankingPeriod period,
        Pageable pageable
        ){
        return ResponseEntity.ok(ApiResponse.success(recipeService.findPopularRecipes(memberId, period, pageable)));
    }

    @GetMapping("/{recipeId}/metadata")
    public ResponseEntity<ApiResponse<RecipeMetadataResponse>> getMetadata(@PathVariable Long recipeId) {
        return ResponseEntity.ok(ApiResponse.success(recipeService.getMetadata(recipeId)));
    }

    @GetMapping("/{recipeId}")
    public ResponseEntity<ApiResponse<RecipeDetailResponse>> getRecipe(
            @LoginMember Long memberId,
            @PathVariable Long recipeId,
            @RequestParam(required = false) Integer servings
    ) {
        return ResponseEntity.ok(ApiResponse.success(recipeFacade.getRecipe(memberId, recipeId, servings)));
    }

    @PostMapping("/ai-recommendations")
    public ResponseEntity<ApiResponse<AiRecipeRecommendResponse>> recommendByAi(
        @LoginMember Long memberId,
        @Valid @RequestBody AiRecipeRecommendRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(aiRecipeRecommendFacade.recommend(memberId, request.text())));
    }

    @PostMapping("/ai-fridge-recommendations")
    public ResponseEntity<ApiResponse<AiFridgeRecommendResponse>> recommendByFridge(
        @LoginMember Long memberId,
        @Valid @RequestBody AiFridgeRecommendRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(aiFridgeRecommendFacade.recommend(memberId, request.text())));
    }

    @GetMapping("/most-viewed")
    public ResponseEntity<ApiResponse<PageResponse<RecipeRankingResponse>>> findMostViewedRecipes(
        @LoginMember Long memberId,
        Pageable pageable
    ){
        return ResponseEntity.ok(ApiResponse.success(recipeService.findMostViewedRecipes(memberId, pageable)));
    }
}
