package com.recipick.domain.recipe.controller;

import com.recipick.domain.recipe.dto.request.AiRecipeRecommendRequest;
import com.recipick.domain.recipe.dto.request.RecipeSearchRequest;
import com.recipick.domain.recipe.dto.response.AiRecipeRecommendResponse;
import com.recipick.domain.recipe.dto.response.RecipeDetailResponse;
import com.recipick.domain.recipe.dto.response.RecipeSummaryResponse;
import com.recipick.domain.recipe.facade.AiRecipeRecommendFacade;
import com.recipick.domain.recipe.service.RecipeService;
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
    private final AiRecipeRecommendFacade aiRecipeRecommendFacade;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RecipeSummaryResponse>>> searchRecipes(
            @LoginMember Long memberId,
            @Valid @ModelAttribute RecipeSearchRequest request,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(recipeService.searchRecipes(memberId, request, pageable)));
    }

    @GetMapping("/{recipeId}")
    public ResponseEntity<ApiResponse<RecipeDetailResponse>> getRecipe(
            @LoginMember Long memberId,
            @PathVariable Long recipeId,
            @RequestParam(required = false) Integer servings
    ) {
        return ResponseEntity.ok(ApiResponse.success(recipeService.getRecipe(memberId, recipeId, servings)));
    }

    @PostMapping("/ai-recommendations")
    public ResponseEntity<ApiResponse<AiRecipeRecommendResponse>> recommendByAi(
        @LoginMember Long memberId,
        @Valid @RequestBody AiRecipeRecommendRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(aiRecipeRecommendFacade.recommend(memberId, request.text())));
    }
}
