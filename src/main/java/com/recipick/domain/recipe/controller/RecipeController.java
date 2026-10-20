package com.recipick.domain.recipe.controller;

import com.recipick.domain.recipe.dto.RecipeDetailResponse;
import com.recipick.domain.recipe.dto.RecipeSearchRequest;
import com.recipick.domain.recipe.dto.RecipeSummaryResponse;
import com.recipick.domain.recipe.service.RecipeService;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recipes")
public class RecipeController {

    private final String MEMBER_ID_HEADER = "X-Member-Id";

    private final RecipeService recipeService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RecipeSummaryResponse>>> searchRecipes(
        @RequestHeader(MEMBER_ID_HEADER) Long memberId,
        @ModelAttribute RecipeSearchRequest request,
        Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(recipeService.searchRecipes(memberId, request, pageable)));
    }

    @GetMapping("/{recipeId}")
    public ResponseEntity<ApiResponse<RecipeDetailResponse>> getRecipe(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long recipeId,
            @RequestParam(required = false) Integer servings) {
        return ResponseEntity.ok(ApiResponse.success(recipeService.getRecipe(memberId, recipeId,
            servings)));
    }
}
