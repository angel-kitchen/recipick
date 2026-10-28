package com.recipick.domain.member.controller;

import com.recipick.domain.member.dto.request.AdminAuditSearchRequest;
import com.recipick.domain.member.dto.response.AdminAuditDetailResponse;
import com.recipick.domain.member.dto.response.AdminAuditSummaryResponse;
import com.recipick.domain.member.service.AdminAuditQueryService;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.response.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminAuditController {
    private final AdminAuditQueryService service;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminAuditSummaryResponse>>> search(
            @Valid @ModelAttribute AdminAuditSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.", service.search(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminAuditDetailResponse>> get(@Positive @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.", service.get(id)));
    }
}
