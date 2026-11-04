package com.recipick.domain.recipe.controller;

import com.recipick.domain.recipe.dto.request.AiPromptCreateRequest;
import com.recipick.domain.recipe.dto.response.AiPromptActiveResponse;
import com.recipick.domain.recipe.dto.response.AiPromptAdminResponse;
import com.recipick.domain.recipe.dto.response.AiPromptVersionResponse;
import com.recipick.domain.recipe.entity.AiPromptType;
import com.recipick.domain.recipe.facade.AiPromptAdminFacade;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.security.LoginAdmin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/ai-prompts")
public class AiPromptAdminController {

    private final AiPromptAdminFacade aiPromptAdminFacade;

    @GetMapping
    public ResponseEntity<ApiResponse<AiPromptAdminResponse>> findPrompts(@RequestParam AiPromptType type) {
        return ResponseEntity.ok(ApiResponse.success(aiPromptAdminFacade.findPrompts(type)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AiPromptVersionResponse>> createPrompt(
        @Valid @RequestBody AiPromptCreateRequest request,
        @LoginAdmin Long adminId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("새 프롬프트 버전을 저장했습니다.", aiPromptAdminFacade.create(request, adminId)));
    }

    @PostMapping("/{promptId}/activate")
    public ResponseEntity<ApiResponse<AiPromptActiveResponse>> activatePrompt(@PathVariable Long promptId) {
        return ResponseEntity.ok(ApiResponse.success("프롬프트 버전을 활성화했습니다.", aiPromptAdminFacade.activate(promptId)));
    }

    @PostMapping("/types/{type}/reset")
    public ResponseEntity<ApiResponse<AiPromptActiveResponse>> resetPrompt(@PathVariable AiPromptType type) {
        return ResponseEntity.ok(ApiResponse.success("기본 프롬프트로 되돌렸습니다.", aiPromptAdminFacade.reset(type)));
    }
}
