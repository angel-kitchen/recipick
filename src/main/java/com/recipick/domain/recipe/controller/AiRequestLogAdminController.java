package com.recipick.domain.recipe.controller;

import com.recipick.domain.recipe.dto.request.AiRequestLogAdminSearchRequest;
import com.recipick.domain.recipe.dto.response.AiRequestLogAdminResponse;
import com.recipick.domain.recipe.facade.AiRequestLogAdminFacade;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/ai-requests")
public class AiRequestLogAdminController {

    private final AiRequestLogAdminFacade aiRequestLogAdminFacade;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AiRequestLogAdminResponse>>> findAiRequests(
        @Valid @ModelAttribute AiRequestLogAdminSearchRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(aiRequestLogAdminFacade.findAiRequests(request)));
    }
}
