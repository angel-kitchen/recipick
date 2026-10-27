package com.recipick.domain.member.controller;

import com.recipick.domain.member.dto.request.MemberPasswordChangeRequest;
import com.recipick.domain.member.dto.response.MemberMeResponse;
import com.recipick.domain.member.facade.MemberMeFacade;
import com.recipick.domain.member.facade.MemberPasswordFacade;
import com.recipick.domain.member.service.AuthCookieFactory;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberMeFacade memberMeFacade;
    private final MemberPasswordFacade memberPasswordFacade;
    private final AuthCookieFactory cookieFactory;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberMeResponse>> getMe(@LoginMember Long memberId) {
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.", memberMeFacade.getMe(memberId)));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(@LoginMember Long memberId,
            @Valid @RequestBody MemberPasswordChangeRequest request) {
        memberPasswordFacade.changePassword(memberId, request);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearRefreshCookie().toString()).build();
    }

}
