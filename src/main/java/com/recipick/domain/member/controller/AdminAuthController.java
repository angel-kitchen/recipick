package com.recipick.domain.member.controller;

import com.recipick.domain.member.dto.AdminLoginResult;
import com.recipick.domain.member.dto.request.AdminLoginRequest;
import com.recipick.domain.member.dto.response.AdminLoginResponse;
import com.recipick.domain.member.service.AdminLoginService;
import com.recipick.domain.member.service.AuthCookieFactory;
import com.recipick.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminLoginService adminLoginService;
    private final AuthCookieFactory cookieFactory;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AdminLoginResponse>> login(@Valid @RequestBody AdminLoginRequest request) {
        AdminLoginResult result = adminLoginService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookieFactory.createRefreshCookie(result.refreshToken(), result.refreshExpiration()).toString())
                .body(ApiResponse.success("로그인되었습니다.", result.response()));
    }
}
