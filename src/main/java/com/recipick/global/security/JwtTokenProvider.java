package com.recipick.global.security;

import com.recipick.global.config.JwtProperties;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
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
        Instant now = clock.instant();
        try {
            Claims claims = Jwts.parser().verifyWith(jwtSigningKey)
                    .clockSkewSeconds(CLOCK_SKEW.toSeconds())
                    .clock(() -> Date.from(now)).build().parseSignedClaims(token).getPayload();
            if (!"ACCESS".equals(claims.get("tokenUse", String.class)) || claims.getExpiration() == null
                    || claims.getIssuedAt() == null || claims.getId() == null || claims.getId().isBlank()
                    || !claims.getExpiration().after(claims.getIssuedAt())
                    || claims.getIssuedAt().toInstant().isAfter(now.plus(CLOCK_SKEW))) {
                throw new IllegalArgumentException("액세스 토큰의 필수 정보가 올바르지 않습니다.");
            }
            String subject = claims.getSubject();
            if (subject == null || !subject.matches("[1-9][0-9]*")) {
                throw new IllegalArgumentException("토큰 주체 ID가 올바르지 않습니다.");
            }
            SubjectType type = SubjectType.valueOf(claims.get("type", String.class));
            Long id = Long.valueOf(subject);
            if (type == SubjectType.ADMIN && !id.equals(claims.get("adminId", Long.class))) {
                throw new IllegalArgumentException("관리자 ID가 일치하지 않습니다.");
            }
            return new AuthPrincipal(type, id, claims.get("role", String.class), claims.get("martId", Long.class));
        } catch (ExpiredJwtException exception) {
            throw new BusinessException(AuthErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
    }

    public JwtTokenPair createMemberTokens(Long memberId) {
        Instant now = clock.instant();
        String refreshId = UUID.randomUUID().toString();
        String accessToken = Jwts.builder()
                .subject(memberId.toString())
                .claim("type", "MEMBER")
                .claim("tokenUse", "ACCESS")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenExpiration())))
                .signWith(jwtSigningKey)
                .compact();
        String refreshToken = Jwts.builder()
                .subject(memberId.toString())
                .claim("type", "MEMBER")
                .claim("tokenUse", "REFRESH")
                .id(refreshId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.refreshTokenExpiration())))
                .signWith(jwtSigningKey)
                .compact();
        return new JwtTokenPair(accessToken, refreshToken, refreshId,
                properties.accessTokenExpiration(), properties.refreshTokenExpiration());
    }
}
