package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.member.dto.request.AdminLoginRequest;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.global.config.LoginProperties;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenPair;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.infra.redis.RedisLoginAttemptStore;
import com.recipick.infra.redis.RedisRefreshTokenStore;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AdminLoginServiceTest {

    private final AdminRepository admins = mock(AdminRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final RedisLoginAttemptStore attempts = mock(RedisLoginAttemptStore.class);
    private final RedisRefreshTokenStore refreshTokens = mock(RedisRefreshTokenStore.class);
    private final JwtTokenProvider tokens = mock(JwtTokenProvider.class);
    private final AdminLoginRequest request = new AdminLoginRequest("super01", "password");
    private AdminLoginService service;

    @BeforeEach
    void setUp() {
        given(encoder.encode(anyString())).willReturn("dummy-hash");
        service = new AdminLoginService(admins, encoder, attempts, refreshTokens, tokens,
                new LoginProperties(3, Duration.ofMinutes(10), Duration.ofMinutes(10), true));
    }

    @Test
    @DisplayName("비밀번호 검사 중 다른 요청이 잠금을 만들면 토큰을 발급하지 않는다")
    void rejectsLockDuringVerification() {
        // given
        stubAdmin(true);
        given(attempts.clearIfUnlocked("ADMIN", request.loginId())).willReturn(false);

        // when
        Throwable thrown = catchThrowable(() -> service.login(request));

        // then
        assertError(thrown, AuthErrorCode.LOGIN_LOCKED);
        verifyNoInteractions(tokens, refreshTokens);
    }

    @Test
    @DisplayName("비활성 관리자에게는 토큰을 발급하거나 저장하지 않는다")
    void rejectsInactiveAdmin() {
        // given
        stubAdmin(false);

        // when
        Throwable thrown = catchThrowable(() -> service.login(request));

        // then
        assertError(thrown, AuthErrorCode.INACTIVE_ADMIN);
        verifyNoInteractions(tokens, refreshTokens);
    }

    @Test
    @DisplayName("리프레시 토큰 저장 실패를 로그인 성공으로 처리하지 않는다")
    void propagatesRefreshStorageFailure() {
        // given
        stubAdmin(true);
        given(attempts.clearIfUnlocked("ADMIN", request.loginId())).willReturn(true);
        JwtTokenPair pair = new JwtTokenPair("access", "refresh", "id", Duration.ofMinutes(30), Duration.ofDays(14));
        given(tokens.createTokens(any(AuthPrincipal.class))).willReturn(pair);
        doThrow(new DataAccessResourceFailureException("Redis unavailable")).when(refreshTokens)
                .save("ADMIN", 1L, "id", "refresh", Duration.ofDays(14));

        // when
        Throwable thrown = catchThrowable(() -> service.login(request));

        // then
        assertThat(thrown).isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    @DisplayName("없는 아이디도 더미 해시 비교를 수행하고 동일한 로그인 실패 오류를 반환한다")
    void comparesDummyHashForMissingAdmin() {
        // given
        given(admins.findByLoginIdIgnoreCase(request.loginId())).willReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service.login(request));

        // then
        assertError(thrown, AuthErrorCode.INVALID_ADMIN_CREDENTIALS);
        verify(encoder).matches(request.password(), "dummy-hash");
        verifyNoInteractions(tokens, refreshTokens);
    }

    @Test
    @DisplayName("Redis 잠금 조회가 실패하면 관리자 조회와 토큰 발급을 진행하지 않는다")
    void stopsWhenRedisUnavailable() {
        // given
        given(attempts.isLocked("ADMIN", request.loginId()))
                .willThrow(new DataAccessResourceFailureException("Redis unavailable"));

        // when
        Throwable thrown = catchThrowable(() -> service.login(request));

        // then
        assertThat(thrown).isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(admins, tokens, refreshTokens);
    }

    private void stubAdmin(boolean active) {
        Admin admin = Admin.builder().loginId(request.loginId()).passwordHash("stored-hash")
                .role(AdminRole.SUPER_ADMIN).active(active).build();
        ReflectionTestUtils.setField(admin, "id", 1L);
        given(admins.findByLoginIdIgnoreCase(request.loginId())).willReturn(Optional.of(admin));
        given(encoder.matches(request.password(), "stored-hash")).willReturn(true);
    }

    private void assertError(Throwable thrown, AuthErrorCode expected) {
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
