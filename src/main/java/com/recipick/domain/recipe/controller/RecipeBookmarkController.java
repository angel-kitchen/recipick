package com.recipick.domain.recipe.controller;

import com.recipick.domain.recipe.dto.response.RecipeBookmarkCreateResponse;
import com.recipick.domain.recipe.dto.response.RecipeBookmarkResponse;
import com.recipick.domain.recipe.service.RecipeBookmarkService;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.security.LoginMember;
import com.recipick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RecipeBookmarkController {

    private final RecipeBookmarkService recipeBookmarkService;

    @PostMapping("/api/recipes/{recipeId}/bookmarks")
    public ResponseEntity<ApiResponse<RecipeBookmarkCreateResponse>> addBookmark(
            @LoginMember Long memberId,
            @PathVariable Long recipeId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(recipeBookmarkService.addBookmark(memberId, recipeId)));
    }

    @DeleteMapping("/api/recipes/{recipeId}/bookmarks")
    public ResponseEntity<ApiResponse<Void>> removeBookmark(
            @LoginMember Long memberId,
            @PathVariable Long recipeId
    ) {
        recipeBookmarkService.removeBookmark(memberId, recipeId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/api/members/me/recipe-bookmarks")
    public ResponseEntity<ApiResponse<PageResponse<RecipeBookmarkResponse>>> findMyBookmarks(
            @LoginMember Long memberId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(recipeBookmarkService.findMyBookmarks(memberId, pageable)));
    }
}
