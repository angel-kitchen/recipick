package com.recipick.domain.product.controller;

import com.recipick.domain.product.dto.CategoryResponse;
import com.recipick.domain.product.service.CategoryService;
import com.recipick.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories(){
        return ResponseEntity.ok(ApiResponse.success(categoryService.getCategories()));
    }
}
