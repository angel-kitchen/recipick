package com.recipick.domain.member.controller;

import com.recipick.domain.member.dto.MemberLoginResult;
import com.recipick.domain.member.dto.request.MemberLoginRequest;
import com.recipick.domain.member.dto.request.SignupRequest;
import com.recipick.domain.member.dto.response.MemberLoginResponse;
import com.recipick.domain.member.dto.response.SignupResponse;
import com.recipick.domain.member.dto.response.TokenReissueResponse;
import com.recipick.domain.member.dto.response.CsrfTokenResponse;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.security.SubjectType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.web.csrf.CsrfToken;
import com.recipick.domain.member.service.AuthCookieFactory;
import com.recipick.domain.member.service.MemberLoginService;
import com.recipick.domain.member.service.SignupService;
import com.recipick.domain.member.service.TokenManagementService;
import com.recipick.global.response.ApiResponse;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenPair;
import jakarta.validation.Valid;
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
    private final AuthCookieFactory cookieFactory;
    private final TokenManagementService tokenManagementService;

    @GetMapping("/csrf")
    public ResponseEntity<ApiResponse<CsrfTokenResponse>> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(ApiResponse.success("요청 보호 정보를 조회했습니다.",
                        new CsrfTokenResponse(token.getHeaderName(), token.getToken())));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<MemberLoginResponse>> login(@Valid @RequestBody MemberLoginRequest request) {
        MemberLoginResult result = memberLoginService.login(request);
        ResponseCookie cookie = cookieFactory.createRefreshCookie(result.refreshToken(), result.refreshExpiration());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString(), cookieFactory.clearLegacyCookie().toString())
                .body(ApiResponse.success("로그인되었습니다.", result.response()));
    }

    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<TokenReissueResponse>> reissue(
            @CookieValue(name = AuthCookieFactory.MEMBER_COOKIE, required = false) String refreshToken) {
        JwtTokenPair tokens = tokenManagementService.reissue(refreshToken, SubjectType.MEMBER);
        TokenReissueResponse response = new TokenReissueResponse(tokens.accessToken(),
                tokens.accessExpiration().toSeconds());
        ResponseCookie cookie = cookieFactory.createRefreshCookie(tokens.refreshToken(), tokens.refreshExpiration());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString(), cookieFactory.clearLegacyCookie().toString())
                .body(ApiResponse.success("토큰이 재발급되었습니다.", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal,
            @CookieValue(name = AuthCookieFactory.MEMBER_COOKIE, required = false) String refreshToken) {
        if (principal != null && principal.type() != SubjectType.MEMBER) {
            throw new BusinessException(AuthErrorCode.ACCESS_DENIED);
        }
        boolean clearCookie = tokenManagementService.ownsRefreshCookie(principal, refreshToken);
        tokenManagementService.logout(principal, refreshToken);
        var response = ResponseEntity.noContent();
        if (clearCookie) {
            response.header(HttpHeaders.SET_COOKIE, cookieFactory.clearRefreshCookie().toString());
        }
        return response.build();
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("회원가입이 완료되었습니다.", signupService.signup(request)));
    }
}
