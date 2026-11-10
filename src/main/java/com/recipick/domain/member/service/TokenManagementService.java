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

    public JwtTokenPair reissue(String refreshToken, SubjectType expectedType) {
        RefreshTokenDetails details = tokenProvider.parseRefreshToken(refreshToken);
        if (details.principal().type() != expectedType) {
            throw new BusinessException(AuthErrorCode.RELOGIN_REQUIRED);
        }
        return reissueDetails(refreshToken, details);
    }

    public JwtTokenPair reissue(String refreshToken) {
        return reissueDetails(refreshToken, tokenProvider.parseRefreshToken(refreshToken));
    }

    private JwtTokenPair reissueDetails(String refreshToken, RefreshTokenDetails details) {
        AuthPrincipal principal = currentPrincipal(details);
        JwtTokenPair tokens = tokenProvider.rotateTokens(principal, details.sessionId(),
                details.refreshTokenVersion(), details.sessionExpiresAt());
        boolean rotated = refreshTokenStore.rotate(principal.type().name(), principal.id(), details.sessionId(),
                refreshToken, tokens.refreshToken(), tokens.refreshExpiration());
        if (!rotated) {
            throw new BusinessException(AuthErrorCode.RELOGIN_REQUIRED);
        }
        // Redis 교체 중 비밀번호가 변경됐으면 이미 무효화된 토큰을 성공 응답으로 내보내지 않는다.
        if (principal.type() == SubjectType.MEMBER) {
            currentPrincipal(details);
        }
        return tokens;
    }

    public boolean ownsRefreshCookie(AuthPrincipal principal, String refreshToken) {
        if (principal == null || refreshToken == null || refreshToken.isBlank()) {
            return false;
        }
        try {
            RefreshTokenDetails details = tokenProvider.parseRefreshToken(refreshToken);
            return principal.type() == details.principal().type()
                    && principal.id().equals(details.principal().id())
                    && java.util.Objects.equals(principal.sessionId(), details.sessionId());
        } catch (BusinessException exception) {
            // 유효하지 않은 Cookie는 정리해도 다른 정상 세션을 지우지 않는다.
            return true;
        }
    }

    public void logout(AuthPrincipal principal, String refreshToken) {
        if (principal == null || principal.sessionId() == null) {
            throw new BusinessException(AuthErrorCode.LOGIN_REQUIRED);
        }
        refreshTokenStore.delete(principal.type().name(), principal.id(), principal.sessionId());
    }

    private AuthPrincipal currentPrincipal(RefreshTokenDetails details) {
        AuthPrincipal principal = details.principal();
        if (principal.type() == SubjectType.MEMBER) {
            if (memberRepository.findById(principal.id())
                    .filter(member -> !member.isWithdrawn()
                            && member.getRefreshTokenVersion() == details.refreshTokenVersion()).isEmpty()) {
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
