package com.recipick.domain.member.controller;

import com.recipick.domain.member.dto.response.MemberMeResponse;
import com.recipick.domain.member.facade.MemberMeFacade;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberMeFacade memberMeFacade;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberMeResponse>> getMe(@LoginMember Long memberId) {
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.", memberMeFacade.getMe(memberId)));
    }
}
