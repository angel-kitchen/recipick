package com.recipick.domain.product.controller;

import com.recipick.domain.product.dto.CategoryCreateRequest;
import com.recipick.domain.product.dto.CategoryIdResponse;
import com.recipick.domain.product.dto.CategoryUpdateRequest;
import com.recipick.domain.product.service.CategoryService;
import com.recipick.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/categories")
public class CategoryAdminController {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<ApiResponse<CategoryIdResponse>> createCategory(
        @Valid @RequestBody CategoryCreateRequest request){
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(categoryService.createCategory(request)));
    }

    @PatchMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryIdResponse>> updateCategory(
        @PathVariable Long categoryId,
        @Valid @RequestBody CategoryUpdateRequest request){
        return ResponseEntity.ok(ApiResponse.success(categoryService.updateCategory(categoryId, request)));
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long categoryId){
        categoryService.delete(categoryId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
