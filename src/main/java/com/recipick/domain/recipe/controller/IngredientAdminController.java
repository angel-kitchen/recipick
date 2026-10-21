package com.recipick.domain.recipe.controller;

import com.recipick.domain.recipe.dto.request.IngredientSaveRequest;
import com.recipick.domain.recipe.dto.response.IngredientAdminResponse;
import com.recipick.domain.recipe.dto.response.IngredientIdResponse;
import com.recipick.domain.recipe.facade.IngredientAdminFacade;
import com.recipick.domain.recipe.service.IngredientService;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/ingredients")
public class IngredientAdminController {

    private final IngredientService ingredientService;
    private final IngredientAdminFacade ingredientAdminFacade;

    @PostMapping
    public ResponseEntity<ApiResponse<IngredientIdResponse>> createIngredient(
            @Valid @RequestBody IngredientSaveRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ingredientService.createIngredient(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<IngredientAdminResponse>>> findIngredients(
            @RequestParam(required = false) String keyword,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(ingredientAdminFacade.findIngredients(keyword, pageable)));
    }

    @PatchMapping("/{ingredientId}")
    public ResponseEntity<ApiResponse<IngredientIdResponse>> updateIngredient(
            @PathVariable Long ingredientId,
            @Valid @RequestBody IngredientSaveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(ingredientAdminFacade.updateIngredient(ingredientId, request)));
    }
}
