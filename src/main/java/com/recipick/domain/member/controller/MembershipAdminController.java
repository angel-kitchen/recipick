package com.recipick.domain.member.controller;

import com.recipick.domain.member.dto.request.MembershipHistoryRequest;
import com.recipick.domain.member.dto.response.MembershipGrantResponse;
import com.recipick.domain.member.dto.response.MembershipHistoryResponse;
import com.recipick.domain.member.facade.MembershipGrantFacade;
import com.recipick.domain.member.service.MembershipHistoryService;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.response.PageResponse;
import com.recipick.global.security.LoginAdmin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/members/{memberId}/memberships")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class MembershipAdminController {

    private final MembershipGrantFacade membershipGrantFacade;
    private final MembershipHistoryService membershipHistoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MembershipHistoryResponse>>> history(
            @Positive @PathVariable Long memberId, @Valid @ModelAttribute MembershipHistoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.", membershipHistoryService.search(memberId, request)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MembershipGrantResponse>> grant(@PathVariable Long memberId,
            @LoginAdmin Long adminId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("멤버십이 지급되었습니다.",
                membershipGrantFacade.grant(memberId, adminId)));
    }
}
