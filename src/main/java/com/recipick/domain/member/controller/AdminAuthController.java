package com.recipick.domain.member.controller;

import com.recipick.domain.member.dto.AdminLoginResult;
import com.recipick.domain.member.dto.request.AdminLoginRequest;
import com.recipick.domain.member.dto.response.AdminLoginResponse;
import com.recipick.domain.member.dto.response.TokenReissueResponse;
import com.recipick.domain.member.service.AdminLoginService;
import com.recipick.domain.member.service.AuthCookieFactory;
import com.recipick.domain.member.service.TokenManagementService;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.SubjectType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
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
    private final TokenManagementService tokenManagementService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AdminLoginResponse>> login(@Valid @RequestBody AdminLoginRequest request) {
        AdminLoginResult result = adminLoginService.login(request);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,
                        cookieFactory.createRefreshCookie(SubjectType.ADMIN,
                                result.refreshToken(), result.refreshExpiration()).toString(),
                        cookieFactory.clearLegacyCookie().toString())
                .body(ApiResponse.success("로그인되었습니다.", result.response()));
    }

    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<TokenReissueResponse>> reissue(
            @CookieValue(name = AuthCookieFactory.ADMIN_COOKIE, required = false) String refreshToken) {
        var tokens = tokenManagementService.reissue(refreshToken, SubjectType.ADMIN);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,
                        cookieFactory.createRefreshCookie(SubjectType.ADMIN,
                                tokens.refreshToken(), tokens.refreshExpiration()).toString())
                .body(ApiResponse.success("토큰이 재발급되었습니다.",
                        new TokenReissueResponse(tokens.accessToken(), tokens.accessExpiration().toSeconds())));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal,
            @CookieValue(name = AuthCookieFactory.ADMIN_COOKIE, required = false) String refreshToken) {
        if (principal == null || principal.type() != SubjectType.ADMIN) {
            throw new BusinessException(AuthErrorCode.ACCESS_DENIED);
        }
        boolean clearCookie = tokenManagementService.ownsRefreshCookie(principal, refreshToken);
        tokenManagementService.logout(principal, refreshToken);
        var response = ResponseEntity.noContent();
        if (clearCookie) {
            response.header(HttpHeaders.SET_COOKIE, cookieFactory.clearRefreshCookie(SubjectType.ADMIN).toString());
        }
        return response.build();
    }
}
