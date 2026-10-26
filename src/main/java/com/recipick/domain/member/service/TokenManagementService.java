package com.recipick.domain.member.service;

import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenPair;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.RefreshTokenDetails;
import com.recipick.global.security.SubjectType;
import com.recipick.infra.redis.RedisRefreshTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenManagementService {

    private final JwtTokenProvider tokenProvider;
    private final RedisRefreshTokenStore refreshTokenStore;
    private final MemberRepository memberRepository;
    private final AdminRepository adminRepository;

    public JwtTokenPair reissue(String refreshToken) {
        RefreshTokenDetails details = tokenProvider.parseRefreshToken(refreshToken);
        AuthPrincipal principal = currentPrincipal(details);
        JwtTokenPair tokens = tokenProvider.rotateTokens(principal, details.sessionId());
        boolean rotated = refreshTokenStore.rotate(principal.type().name(), principal.id(), details.sessionId(),
                refreshToken, tokens.refreshToken(), tokens.refreshExpiration());
        if (!rotated) {
            throw new BusinessException(AuthErrorCode.RELOGIN_REQUIRED);
        }
        return tokens;
    }

    public void logout(AuthPrincipal principal, String refreshToken) {
        if (principal == null) {
            throw new BusinessException(AuthErrorCode.LOGIN_REQUIRED);
        }
        if (principal.sessionId() != null) {
            refreshTokenStore.delete(principal.type().name(), principal.id(), principal.sessionId());
            return;
        }
        // 이전 형식 액세스 토큰에는 sid가 없으므로 본인 리프레시 쿠키가 있을 때만 세션을 찾는다.
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        RefreshTokenDetails details;
        try {
            details = tokenProvider.parseRefreshToken(refreshToken);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != AuthErrorCode.RELOGIN_REQUIRED) {
                throw exception;
            }
            // 인증된 로그아웃은 잘못됐거나 만료된 쿠키도 제거하고 멱등 성공한다.
            return;
        }
        if (principal.type() == details.principal().type() && principal.id().equals(details.principal().id())) {
            refreshTokenStore.delete(principal.type().name(), principal.id(), details.sessionId());
        }
    }

    private AuthPrincipal currentPrincipal(RefreshTokenDetails details) {
        AuthPrincipal principal = details.principal();
        if (principal.type() == SubjectType.MEMBER) {
            if (!memberRepository.existsByIdAndWithdrawnAtIsNull(principal.id())) {
                throw new BusinessException(AuthErrorCode.RELOGIN_REQUIRED);
            }
            return principal;
        }
        Admin admin = adminRepository.findById(principal.id())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.RELOGIN_REQUIRED));
        if (!admin.isActive()) {
            refreshTokenStore.delete(principal.type().name(), principal.id(), details.sessionId());
            throw new BusinessException(AuthErrorCode.INACTIVE_ADMIN);
        }
        return new AuthPrincipal(SubjectType.ADMIN, admin.getId(), admin.getRole().name(), admin.getMartId());
    }
}
