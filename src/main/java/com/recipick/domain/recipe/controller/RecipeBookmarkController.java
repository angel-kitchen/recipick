package com.recipick.domain.recipe.controller;

import com.recipick.domain.recipe.dto.response.RecipeBookmarkCreateResponse;
import com.recipick.domain.recipe.dto.response.RecipeBookmarkResponse;
import com.recipick.domain.recipe.service.RecipeBookmarkService;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class RecipeBookmarkController {

    private static final String MEMBER_ID_HEADER = "X-Member-Id";

    private final RecipeBookmarkService recipeBookmarkService;

    @PostMapping("/api/recipes/{recipeId}/bookmarks")
    public ResponseEntity<ApiResponse<RecipeBookmarkCreateResponse>> addBookmark(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long recipeId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(recipeBookmarkService.addBookmark(memberId, recipeId)));
    }

    @DeleteMapping("/api/recipes/{recipeId}/bookmarks")
    public ResponseEntity<ApiResponse<Void>> removeBookmark(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long recipeId
    ) {
        recipeBookmarkService.removeBookmark(memberId, recipeId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/api/member/me/recipe-bookmarks")
    public ResponseEntity<ApiResponse<PageResponse<RecipeBookmarkResponse>>> findMyBookmarks(
        @RequestHeader(MEMBER_ID_HEADER) Long memberId,
        Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(recipeBookmarkService.findMyBookmarks(memberId, pageable)));
    }
}
