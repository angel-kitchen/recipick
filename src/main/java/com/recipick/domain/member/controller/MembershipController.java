package com.recipick.domain.member.controller;

import com.recipick.domain.member.dto.response.MembershipMeResponse;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memberships")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService membershipService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MembershipMeResponse>> getMe(@LoginMember Long memberId) {
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.", membershipService.getMe(memberId)));
    }
}
