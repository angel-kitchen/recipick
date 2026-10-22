package com.recipick.domain.recipe.controller;

import com.recipick.domain.recipe.dto.request.RecipeAdminSearchRequest;
import com.recipick.domain.recipe.dto.request.RecipeIngredientsSaveRequest;
import com.recipick.domain.recipe.dto.request.RecipeSaveRequest;
import com.recipick.domain.recipe.dto.request.RecipeStatusUpdateRequest;
import com.recipick.domain.recipe.dto.response.RecipeAdminDetailResponse;
import com.recipick.domain.recipe.dto.response.RecipeAdminSummaryResponse;
import com.recipick.domain.recipe.dto.response.RecipeIdResponse;
import com.recipick.domain.recipe.dto.response.RecipeIngredientsSaveResponse;
import com.recipick.domain.recipe.facade.RecipeAdminFacade;
import com.recipick.domain.recipe.service.RecipeAdminService;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/recipes")
public class RecipeAdminController {

    private final RecipeAdminService recipeAdminService;
    private final RecipeAdminFacade recipeAdminFacade;

    @PostMapping
    public ResponseEntity<ApiResponse<RecipeIdResponse>> createRecipe(
        @Valid @RequestBody RecipeSaveRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(recipeAdminService.createRecipe(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RecipeAdminSummaryResponse>>> searchRecipes(
        @Valid @ModelAttribute RecipeAdminSearchRequest request,
        Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(recipeAdminFacade.searchRecipes(request, pageable)));
    }

    @GetMapping("/{recipeId}")
    public ResponseEntity<ApiResponse<RecipeAdminDetailResponse>> getRecipe(@PathVariable Long recipeId) {
        return ResponseEntity.ok(ApiResponse.success(recipeAdminFacade.getRecipe(recipeId)));
    }

    @PatchMapping("/{recipeId}")
    public ResponseEntity<ApiResponse<RecipeIdResponse>> updateRecipe(
        @PathVariable Long recipeId,
        @Valid @RequestBody RecipeSaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(recipeAdminService.updateRecipe(recipeId, request)));
    }

    @PatchMapping("/{recipeId}/status")
    public ResponseEntity<ApiResponse<RecipeIdResponse>> changeStatus(
        @PathVariable Long recipeId,
        @Valid @RequestBody RecipeStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(recipeAdminService.changeStatus(recipeId, request)));
    }

    @PutMapping("/{recipeId}/ingredients")
    public ResponseEntity<ApiResponse<RecipeIngredientsSaveResponse>> saveIngredients(
        @PathVariable Long recipeId,
        @Valid @RequestBody RecipeIngredientsSaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(recipeAdminFacade.saveIngredients(recipeId, request)));
    }
}
