package com.recipick.domain.recipe.controller;

import com.recipick.domain.recipe.dto.request.AiAutoCartPreviewRequest;
import com.recipick.domain.recipe.dto.request.AutoCartConfirmRequest;
import com.recipick.domain.recipe.dto.request.AutoCartPreviewRequest;
import com.recipick.domain.recipe.dto.response.AutoCartConfirmResponse;
import com.recipick.domain.recipe.dto.response.AiAutoCartPreviewResponse;
import com.recipick.domain.recipe.dto.response.AutoCartPreviewResponse;
import com.recipick.domain.recipe.dto.response.AutoCartSummaryResponse;
import com.recipick.domain.recipe.facade.AiAutoCartFacade;
import com.recipick.domain.recipe.facade.AutoCartFacade;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.response.PageResponse;
import com.recipick.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auto-carts")
public class AutoCartController {

    private final AutoCartFacade autoCartFacade;
    private final AiAutoCartFacade aiAutoCartFacade;

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<AutoCartPreviewResponse>> preview(
        @LoginMember Long memberId,
        @Valid @RequestBody AutoCartPreviewRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(autoCartFacade.preview(memberId, request)));
    }

    @PostMapping("/ai-preview")
    public ResponseEntity<ApiResponse<AiAutoCartPreviewResponse>> previewByAi(
        @LoginMember Long memberId,
        @Valid @RequestBody AiAutoCartPreviewRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(aiAutoCartFacade.preview(memberId, request.text())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AutoCartSummaryResponse>>> findAutoCarts(
        @LoginMember Long memberId,
        Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(autoCartFacade.findAutoCarts(memberId, pageable)));
    }

    @GetMapping("/{autoCartRequestId}")
    public ResponseEntity<ApiResponse<AutoCartPreviewResponse>> getAutoCart(
        @LoginMember Long memberId,
        @PathVariable Long autoCartRequestId
    ) {
        return ResponseEntity.ok(ApiResponse.success(autoCartFacade.getAutoCart(memberId, autoCartRequestId)));
    }

    @PostMapping("/{autoCartRequestId}/confirm")
    public ResponseEntity<ApiResponse<AutoCartConfirmResponse>> confirm(
        @LoginMember Long memberId,
        @PathVariable Long autoCartRequestId,
        @Valid @RequestBody(required = false) AutoCartConfirmRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(autoCartFacade.confirm(memberId, autoCartRequestId, request)));
    }
}
