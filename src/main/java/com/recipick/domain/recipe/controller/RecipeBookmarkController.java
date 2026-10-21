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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RecipeBookmarkController {

    // TODO: @LoginMember(global/security) 머지 후 교체. 헤더 값은 검증되지 않으므로 그 전까지 Postman 테스트용으로만 쓴다
    private static final String MEMBER_ID_HEADER = "X-Member-Id";

    private final RecipeBookmarkService recipeBookmarkService;

    @PostMapping("/api/recipes/{recipeId}/bookmarks")
    public ResponseEntity<ApiResponse<RecipeBookmarkCreateResponse>> addBookmark(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long recipeId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(recipeBookmarkService.addBookmark(memberId, recipeId)));
    }

    @DeleteMapping("/api/recipes/{recipeId}/bookmarks")
    public ResponseEntity<ApiResponse<Void>> removeBookmark(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long recipeId
    ) {
        recipeBookmarkService.removeBookmark(memberId, recipeId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/api/members/me/recipe-bookmarks")
    public ResponseEntity<ApiResponse<PageResponse<RecipeBookmarkResponse>>> findMyBookmarks(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(recipeBookmarkService.findMyBookmarks(memberId, pageable)));
    }
}
