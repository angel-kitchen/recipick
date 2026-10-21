package com.recipick.domain.member.controller;

import com.recipick.domain.member.dto.MemberLoginResult;
import com.recipick.domain.member.dto.request.MemberLoginRequest;
import com.recipick.domain.member.dto.response.MemberLoginResponse;
import com.recipick.domain.member.service.MemberLoginService;
import com.recipick.global.config.LoginProperties;
import com.recipick.domain.member.dto.SignupRequest;
import com.recipick.domain.member.dto.SignupResponse;
import com.recipick.domain.member.service.SignupService;
import com.recipick.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SignupService signupService;
    private final MemberLoginService memberLoginService;
    private final LoginProperties loginProperties;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<MemberLoginResponse>> login(@Valid @RequestBody MemberLoginRequest request) {
        MemberLoginResult result = memberLoginService.login(request);
        ResponseCookie cookie = ResponseCookie.from("refreshToken", result.refreshToken())
                .httpOnly(true)
                .secure(loginProperties.refreshCookieSecure())
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(result.refreshExpiration())
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success("로그인되었습니다.", result.response()));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("회원가입이 완료되었습니다.", signupService.signup(request)));
    }
}
