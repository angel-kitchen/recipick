package com.recipick.domain.member.controller;

import com.recipick.domain.member.dto.request.MemberAdminSearchRequest;
import com.recipick.domain.member.dto.response.MemberAdminResponse;
import com.recipick.domain.member.service.MemberAdminService;
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
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class MemberAdminController {

    private final MemberAdminService memberAdminService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MemberAdminResponse>>> findMembers(
            @Valid @ModelAttribute MemberAdminSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.", memberAdminService.findMembers(request)));
    }
}
