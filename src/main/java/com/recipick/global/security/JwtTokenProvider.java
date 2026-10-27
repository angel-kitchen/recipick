package com.recipick.global.security;

import com.recipick.global.config.JwtProperties;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    // 발급 서버와 검증 서버 사이의 작은 시계 차이로 정상 토큰을 거절하지 않도록 한다.
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(5);

    private final SecretKey jwtSigningKey;
    private final JwtProperties properties;
    private final Clock clock;

    public AuthPrincipal parseAccessToken(String token) {
        try {
            return principal(parseClaims(token, "ACCESS"));
        } catch (ExpiredJwtException exception) {
            throw new BusinessException(AuthErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
    }

    public RefreshTokenDetails parseRefreshToken(String token) {
        try {
            Claims claims = parseClaims(token, "REFRESH");
            AuthPrincipal principal = principal(claims);
            // sid 도입 전 발급된 리프레시 토큰은 원래 jti가 Redis 저장 위치다.
            String sessionId = principal.sessionId() == null ? claims.getId() : principal.sessionId();
            if (!sessionId.matches("[A-Za-z0-9-]{1,128}")) {
                throw new IllegalArgumentException("로그인 세션 식별자가 올바르지 않습니다.");
            }
            Long version = claims.get("refreshVersion", Long.class);
            if (version != null && version < 0) {
                throw new IllegalArgumentException("리프레시 버전이 올바르지 않습니다.");
            }
            return new RefreshTokenDetails(principal, claims.getId(), sessionId, version == null ? 0 : version);
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(AuthErrorCode.RELOGIN_REQUIRED);
        }
    }

    private Claims parseClaims(String token, String tokenUse) {
        Instant now = clock.instant();
        Claims claims = Jwts.parser().verifyWith(jwtSigningKey).clockSkewSeconds(CLOCK_SKEW.toSeconds())
                .clock(() -> Date.from(now)).build().parseSignedClaims(token).getPayload();
        if (!tokenUse.equals(claims.get("tokenUse", String.class)) || claims.getExpiration() == null
                || claims.getIssuedAt() == null || claims.getId() == null || claims.getId().isBlank()
                || !claims.getExpiration().after(claims.getIssuedAt())
                || claims.getIssuedAt().toInstant().isAfter(now.plus(CLOCK_SKEW))) {
            throw new IllegalArgumentException("토큰의 필수 정보가 올바르지 않습니다.");
        }
        return claims;
    }

    private AuthPrincipal principal(Claims claims) {
        String subject = claims.getSubject();
        if (subject == null || !subject.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("토큰 주체 ID가 올바르지 않습니다.");
        }
        SubjectType type = SubjectType.valueOf(claims.get("type", String.class));
        Long id = Long.valueOf(subject);
        if (type == SubjectType.ADMIN && !id.equals(claims.get("adminId", Long.class))) {
            throw new IllegalArgumentException("관리자 ID가 일치하지 않습니다.");
        }
        return new AuthPrincipal(type, id, claims.get("role", String.class), claims.get("martId", Long.class),
                claims.get("sid", String.class));
    }

    public JwtTokenPair createMemberTokens(Long memberId) {
        return createMemberTokens(memberId, 0);
    }

    public JwtTokenPair createMemberTokens(Long memberId, long refreshTokenVersion) {
        if (refreshTokenVersion < 0) {
            throw new IllegalArgumentException("리프레시 버전이 올바르지 않습니다.");
        }
        String refreshId = UUID.randomUUID().toString();
        return issueTokens(new AuthPrincipal(SubjectType.MEMBER, memberId, null, null),
                refreshId, refreshId, refreshTokenVersion);
    }

    public JwtTokenPair createTokens(AuthPrincipal principal) {
        String refreshId = UUID.randomUUID().toString();
        return issueTokens(principal, refreshId, refreshId, 0);
    }

    public JwtTokenPair rotateTokens(AuthPrincipal principal, String sessionId) {
        return rotateTokens(principal, sessionId, 0);
    }

    public JwtTokenPair rotateTokens(AuthPrincipal principal, String sessionId, long refreshTokenVersion) {
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9-]{1,128}")) {
            throw new IllegalArgumentException("로그인 세션 식별자가 올바르지 않습니다.");
        }
        return issueTokens(principal, UUID.randomUUID().toString(), sessionId, refreshTokenVersion);
    }

    private JwtTokenPair issueTokens(AuthPrincipal principal, String refreshId, String sessionId, long version) {
        Instant now = clock.instant();
        String accessToken = builder(principal, "ACCESS", UUID.randomUUID().toString(), sessionId,
                now, properties.accessTokenExpiration()).compact();
        JwtBuilder refreshBuilder = builder(principal, "REFRESH", refreshId, sessionId,
                now, properties.refreshTokenExpiration());
        if (principal.type() == SubjectType.MEMBER) {
            refreshBuilder.claim("refreshVersion", version);
        }
        String refreshToken = refreshBuilder.compact();
        return new JwtTokenPair(accessToken, refreshToken, refreshId,
                properties.accessTokenExpiration(), properties.refreshTokenExpiration());
    }

    private JwtBuilder builder(AuthPrincipal principal, String tokenUse, String tokenId, String sessionId,
            Instant issuedAt, Duration expiration) {
        JwtBuilder builder = Jwts.builder().subject(principal.id().toString()).claim("type", principal.type().name())
                .claim("tokenUse", tokenUse).claim("sid", sessionId).id(tokenId).issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(expiration))).signWith(jwtSigningKey);
        if (principal.type() == SubjectType.ADMIN) {
            builder.claim("adminId", principal.id()).claim("role", principal.role())
                    .claim("martId", principal.martId());
        }
        return builder;
    }
}
