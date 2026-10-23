package com.recipick.domain.member.controller;

import com.recipick.domain.member.dto.response.MembershipGrantResponse;
import com.recipick.domain.member.service.MembershipGrantService;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.security.LoginAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/members/{memberId}/memberships")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class MembershipAdminController {

    private final MembershipGrantService membershipGrantService;

    @PostMapping
    public ResponseEntity<ApiResponse<MembershipGrantResponse>> grant(@PathVariable Long memberId,
            @LoginAdmin Long adminId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("멤버십이 지급되었습니다.",
                membershipGrantService.grant(memberId, adminId)));
    }
}
