package com.recipick.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.global.config.JwtProperties;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class JwtTokenProviderTest {

    private static final Instant NOW = Instant.parse("2026-10-21T05:00:00Z");
    private final SecretKey key = Jwts.SIG.HS256.key().build();
    private final JwtTokenProvider provider = new JwtTokenProvider(key,
            new JwtProperties(null, Duration.ofMinutes(30), Duration.ofDays(14)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("발급한 회원 액세스 토큰에서 회원 ID와 MEMBER 권한을 얻는다")
    void parsesIssuedAccessToken() {
        // given
        JwtTokenPair tokens = provider.createMemberTokens(17L);

        // when
        AuthPrincipal principal = provider.parseAccessToken(tokens.accessToken());

        // then
        assertThat(principal.id()).isEqualTo(17L);
        assertThat(principal.type()).isEqualTo(SubjectType.MEMBER);
        assertThat(principal.role()).isNull();
        assertThat(principal.martId()).isNull();
        assertThat(principal.authorities()).extracting("authority").containsExactly("ROLE_MEMBER");
    }

    @Test
    @DisplayName("리프레시 토큰은 서명이 정상이어도 액세스 토큰으로 사용할 수 없다")
    void rejectsRefreshToken() {
        // given
        String token = provider.createMemberTokens(17L).refreshToken();

        // when
        Throwable thrown = catchThrowable(() -> provider.parseAccessToken(token));

        // then
        assertError(thrown, AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("만료된 토큰은 형식 오류와 구분하여 AUTH-003으로 거절한다")
    void rejectsExpiredToken() {
        // given
        String token = token(Map.of("exp", Date.from(NOW.minusSeconds(6)),
                "iat", Date.from(NOW.minusSeconds(60))));

        // when
        Throwable thrown = catchThrowable(() -> provider.parseAccessToken(token));

        // then
        assertError(thrown, AuthErrorCode.EXPIRED_TOKEN);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 4, 5})
    @DisplayName("발급 시각이 검증 서버보다 최대 5초 빠르면 허용한다")
    void acceptsIssuedAtWithinClockSkew(int seconds) {
        // given
        String token = token(Map.of("iat", Date.from(NOW.plusSeconds(seconds))));

        // when
        AuthPrincipal principal = provider.parseAccessToken(token);

        // then
        assertThat(principal.id()).isEqualTo(17L);
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 30})
    @DisplayName("발급 시각이 5초 허용 오차를 초과하면 AUTH-002로 거절한다")
    void rejectsIssuedAtBeyondClockSkew(int seconds) {
        // given
        String token = token(Map.of("iat", Date.from(NOW.plusSeconds(seconds))));

        // when
        Throwable thrown = catchThrowable(() -> provider.parseAccessToken(token));

        // then
        assertError(thrown, AuthErrorCode.INVALID_TOKEN);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 5})
    @DisplayName("만료 시각이 지났어도 5초 허용 오차 안이면 허용한다")
    void acceptsExpirationWithinClockSkew(int seconds) {
        // given
        String token = token(Map.of("exp", Date.from(NOW.minusSeconds(seconds)),
                "iat", Date.from(NOW.minusSeconds(60))));

        // when
        AuthPrincipal principal = provider.parseAccessToken(token);

        // then
        assertThat(principal.id()).isEqualTo(17L);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    @DisplayName("사용 가능 시각도 동일한 5초 허용 오차를 적용한다")
    void acceptsNotBeforeWithinClockSkew(int seconds) {
        // given
        String token = token(Map.of("nbf", Date.from(NOW.plusSeconds(seconds))));

        // when
        AuthPrincipal principal = provider.parseAccessToken(token);

        // then
        assertThat(principal.id()).isEqualTo(17L);
    }

    @Test
    @DisplayName("사용 가능 시각이 5초 허용 오차를 초과하면 AUTH-002로 거절한다")
    void rejectsNotBeforeBeyondClockSkew() {
        // given
        String token = token(Map.of("nbf", Date.from(NOW.plusSeconds(6))));

        // when
        Throwable thrown = catchThrowable(() -> provider.parseAccessToken(token));

        // then
        assertError(thrown, AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 AUTH-002로 거절한다")
    void rejectsWrongSignature() {
        // given
        String token = Jwts.builder().subject("17").signWith(Jwts.SIG.HS256.key().build()).compact();

        // when
        Throwable thrown = catchThrowable(() -> provider.parseAccessToken(token));

        // then
        assertError(thrown, AuthErrorCode.INVALID_TOKEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-jwt", "1.2.3"})
    @DisplayName("비어 있거나 형식이 잘못된 토큰은 AUTH-002로 거절한다")
    void rejectsMalformedToken(String token) {
        // given
        String input = token;

        // when
        Throwable thrown = catchThrowable(() -> provider.parseAccessToken(input));

        // then
        assertError(thrown, AuthErrorCode.INVALID_TOKEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "9223372036854775808"})
    @DisplayName("주체 ID는 양수 Long 형식이어야 한다")
    void validatesSubject(String subject) {
        // given
        String token = token(Map.of("sub", subject));

        // when
        Throwable thrown = catchThrowable(() -> provider.parseAccessToken(token));

        // then
        assertError(thrown, AuthErrorCode.INVALID_TOKEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"exp", "iat", "jti", "type", "tokenUse", "sub"})
    @DisplayName("필수 클레임이 없는 토큰은 거절한다")
    void rejectsMissingClaim(String field) {
        // given
        Map<String, Object> overrides = new HashMap<>();
        overrides.put(field, null);
        String token = token(overrides);

        // when
        Throwable thrown = catchThrowable(() -> provider.parseAccessToken(token));

        // then
        assertError(thrown, AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("회원 토큰에 관리자 역할을 넣어도 관리자 권한을 얻을 수 없다")
    void rejectsRoleOnMemberToken() {
        // given
        String token = token(Map.of("role", "SUPER_ADMIN"));

        // when
        Throwable thrown = catchThrowable(() -> provider.parseAccessToken(token));

        // then
        assertError(thrown, AuthErrorCode.INVALID_TOKEN);
    }

    @ParameterizedTest
    @CsvSource({"SUPER_ADMIN,", "MART_ADMIN,3"})
    @DisplayName("정상 관리자 역할과 마트 범위는 Principal에 보존한다")
    void parsesAdminScope(String role, Long martId) {
        // given
        Map<String, Object> overrides = new HashMap<>(Map.of("type", "ADMIN", "adminId", 17L, "role", role));
        overrides.put("martId", martId);
        String token = token(overrides);

        // when
        AuthPrincipal principal = provider.parseAccessToken(token);

        // then
        assertThat(principal.type()).isEqualTo(SubjectType.ADMIN);
        assertThat(principal.id()).isEqualTo(17L);
        assertThat(principal.role()).isEqualTo(role);
        assertThat(principal.martId()).isEqualTo(martId);
    }

    @ParameterizedTest
    @CsvSource({"SUPER_ADMIN,3", "MART_ADMIN,", "MART_ADMIN,0", "UNKNOWN,3"})
    @DisplayName("관리자 역할과 소속 마트의 조합이 잘못되면 거절한다")
    void rejectsInvalidAdminScope(String role, Long martId) {
        // given
        Map<String, Object> overrides = new HashMap<>(Map.of("type", "ADMIN", "adminId", 17L, "role", role));
        overrides.put("martId", martId);
        String token = token(overrides);

        // when
        Throwable thrown = catchThrowable(() -> provider.parseAccessToken(token));

        // then
        assertError(thrown, AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("관리자 ID가 subject와 다르면 거절한다")
    void rejectsMismatchedAdminId() {
        // given
        String token = token(Map.of("type", "ADMIN", "adminId", 18L, "role", "SUPER_ADMIN"));

        // when
        Throwable thrown = catchThrowable(() -> provider.parseAccessToken(token));

        // then
        assertError(thrown, AuthErrorCode.INVALID_TOKEN);
    }

    private String token(Map<String, Object> overrides) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "17");
        claims.put("type", "MEMBER");
        claims.put("tokenUse", "ACCESS");
        claims.put("iat", Date.from(NOW.minusSeconds(1)));
        claims.put("exp", Date.from(NOW.plusSeconds(120)));
        claims.put("jti", UUID.randomUUID().toString());
        claims.putAll(overrides);
        return Jwts.builder().claims(claims).signWith(key).compact();
    }

    private void assertError(Throwable thrown, AuthErrorCode expected) {
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
