package com.recipick.domain.member.controller;

import com.recipick.domain.member.dto.MemberLoginResult;
import com.recipick.domain.member.dto.SignupRequest;
import com.recipick.domain.member.dto.SignupResponse;
import com.recipick.domain.member.dto.request.MemberLoginRequest;
import com.recipick.domain.member.dto.response.MemberLoginResponse;
import com.recipick.domain.member.dto.response.TokenReissueResponse;
import com.recipick.domain.member.service.MemberLoginService;
import com.recipick.domain.member.service.SignupService;
import com.recipick.domain.member.service.TokenManagementService;
import com.recipick.global.config.LoginProperties;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenPair;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
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
    private final TokenManagementService tokenManagementService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<MemberLoginResponse>> login(@Valid @RequestBody MemberLoginRequest request) {
        MemberLoginResult result = memberLoginService.login(request);
        ResponseCookie cookie = refreshCookie(result.refreshToken(), result.refreshExpiration());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success("로그인되었습니다.", result.response()));
    }

    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<TokenReissueResponse>> reissue(
            @CookieValue(name = "refreshToken", required = false) String refreshToken) {
        JwtTokenPair tokens = tokenManagementService.reissue(refreshToken);
        TokenReissueResponse response = new TokenReissueResponse(tokens.accessToken(),
                tokens.accessExpiration().toSeconds());
        ResponseCookie cookie = refreshCookie(tokens.refreshToken(), tokens.refreshExpiration());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success("토큰이 재발급되었습니다.", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal,
            @CookieValue(name = "refreshToken", required = false) String refreshToken) {
        tokenManagementService.logout(principal, refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseCookie refreshCookie(String token, Duration expiration) {
        return ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(loginProperties.refreshCookieSecure())
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(expiration)
                .build();
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("회원가입이 완료되었습니다.", signupService.signup(request)));
    }
}
