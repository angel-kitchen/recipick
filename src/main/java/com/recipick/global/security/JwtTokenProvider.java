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
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(5);
    private static final int AUTH_SCHEMA = 2;

    private final SecretKey jwtSigningKey;
    private final JwtProperties properties;
    private final Clock clock;

    public AuthPrincipal parseAccessToken(String token) {
        return parseAccessTokenDetails(token).principal();
    }

    public AccessTokenDetails parseAccessTokenDetails(String token) {
        try {
            Claims claims = parseClaims(token, "ACCESS");
            Long version = claims.get("tokenVersion", Long.class);
            if (version == null || version < 0) {
                throw new IllegalArgumentException("액세스 버전이 올바르지 않습니다.");
            }
            return new AccessTokenDetails(principal(claims), version);
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
            Long version = claims.get("refreshVersion", Long.class);
            if (version == null || version < 0) {
                throw new IllegalArgumentException("리프레시 버전이 올바르지 않습니다.");
            }
            return new RefreshTokenDetails(principal, claims.getId(), principal.sessionId(), version,
                    Instant.ofEpochSecond(claims.get("sessionExpiresAt", Long.class)));
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(AuthErrorCode.RELOGIN_REQUIRED);
        }
    }

    private Claims parseClaims(String token, String tokenUse) {
        Instant now = clock.instant();
        Claims claims = Jwts.parser().verifyWith(jwtSigningKey).clockSkewSeconds(CLOCK_SKEW.toSeconds())
                .clock(() -> Date.from(now)).build().parseSignedClaims(token).getPayload();
        Integer schema = claims.get("authSchema", Integer.class);
        Long deadline = claims.get("sessionExpiresAt", Long.class);
        String sessionId = claims.get("sid", String.class);
        if (!tokenUse.equals(claims.get("tokenUse", String.class)) || claims.getExpiration() == null
                || claims.getIssuedAt() == null || claims.getId() == null || claims.getId().isBlank()
                || !claims.getExpiration().after(claims.getIssuedAt())
                || claims.getIssuedAt().toInstant().isAfter(now.plus(CLOCK_SKEW))
                || schema == null || schema != AUTH_SCHEMA || deadline == null
                || !now.isBefore(Instant.ofEpochSecond(deadline))
                || claims.getExpiration().toInstant().isAfter(Instant.ofEpochSecond(deadline))
                || sessionId == null || !sessionId.matches("[A-Za-z0-9-]{1,128}")) {
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

    public JwtTokenPair createMemberTokens(Long memberId, long version) {
        return createTokens(new AuthPrincipal(SubjectType.MEMBER, memberId, null, null), version);
    }

    public JwtTokenPair createTokens(AuthPrincipal principal) {
        return createTokens(principal, 0);
    }

    private JwtTokenPair createTokens(AuthPrincipal principal, long version) {
        String sessionId = UUID.randomUUID().toString();
        return issueTokens(principal, sessionId, sessionId, version,
                clock.instant().truncatedTo(ChronoUnit.SECONDS).plus(properties.refreshTokenExpiration()));
    }

    public JwtTokenPair rotateTokens(AuthPrincipal principal, String sessionId, long version, Instant deadline) {
        return issueTokens(principal, UUID.randomUUID().toString(), sessionId, version, deadline);
    }

    private JwtTokenPair issueTokens(AuthPrincipal principal, String tokenId, String sessionId, long version,
            Instant deadline) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        if (version < 0 || sessionId == null || !sessionId.matches("[A-Za-z0-9-]{1,128}")
                || deadline == null) {
            throw new IllegalArgumentException("로그인 세션 정보가 올바르지 않습니다.");
        }
        if (!now.isBefore(deadline)) {
            throw new BusinessException(AuthErrorCode.RELOGIN_REQUIRED);
        }
        Duration remaining = Duration.between(now, deadline);
        Duration accessExpiration = remaining.compareTo(properties.accessTokenExpiration()) < 0
                ? remaining : properties.accessTokenExpiration();
        Duration refreshExpiration = remaining.compareTo(properties.refreshTokenExpiration()) < 0
                ? remaining : properties.refreshTokenExpiration();
        String access = builder(principal, "ACCESS", UUID.randomUUID().toString(), sessionId, now,
                accessExpiration, deadline).claim("tokenVersion", version).compact();
        String refresh = builder(principal, "REFRESH", tokenId, sessionId, now,
                refreshExpiration, deadline).claim("refreshVersion", version).compact();
        return new JwtTokenPair(access, refresh, tokenId, accessExpiration, refreshExpiration);
    }

    private JwtBuilder builder(AuthPrincipal principal, String use, String tokenId, String sessionId,
            Instant issuedAt, Duration expiration, Instant deadline) {
        JwtBuilder builder = Jwts.builder().subject(principal.id().toString()).claim("type", principal.type().name())
                .claim("tokenUse", use).claim("sid", sessionId).claim("authSchema", AUTH_SCHEMA)
                .claim("sessionExpiresAt", deadline.getEpochSecond()).id(tokenId).issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(expiration))).signWith(jwtSigningKey);
        if (principal.type() == SubjectType.ADMIN) {
            builder.claim("adminId", principal.id()).claim("role", principal.role()).claim("martId", principal.martId());
        }
        return builder;
    }
}
