package com.recipick.global.security;

import com.recipick.global.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final SecretKey jwtSigningKey;
    private final JwtProperties properties;

    public JwtTokenPair createMemberTokens(Long memberId) {
        Instant now = Instant.now();
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
