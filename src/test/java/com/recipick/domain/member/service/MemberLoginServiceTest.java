package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.member.dto.request.MemberLoginRequest;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.global.config.LoginProperties;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
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

class MemberLoginServiceTest {

    private final MemberRepository members = mock(MemberRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final RedisLoginAttemptStore attempts = mock(RedisLoginAttemptStore.class);
    private final RedisRefreshTokenStore refreshTokens = mock(RedisRefreshTokenStore.class);
    private final JwtTokenProvider tokens = mock(JwtTokenProvider.class);
    private final MemberLoginRequest request = new MemberLoginRequest("user@example.com", "password");
    private MemberLoginService service;

    @BeforeEach
    void setUp() {
        given(encoder.encode(anyString())).willReturn("dummy-hash");
        service = new MemberLoginService(members, encoder, attempts, refreshTokens, tokens,
                new LoginProperties(3, Duration.ofMinutes(10), Duration.ofMinutes(10), true));
    }

    @Test
    @DisplayName("비밀번호 확인 중 잠금이 설정되면 올바른 비밀번호여도 토큰을 발급하지 않는다")
    void rejectsWhenLockedDuringPasswordCheck() {
        // given
        stubValidMember();
        given(attempts.clearIfUnlocked("MEMBER", request.email())).willReturn(false);

        // when
        Throwable thrown = catchThrowable(() -> service.login(request));

        // then
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.LOGIN_LOCKED));
        verifyNoInteractions(tokens, refreshTokens);
    }

    @Test
    @DisplayName("리프레시 토큰 저장에 실패하면 정상 로그인 결과를 반환하지 않는다")
    void propagatesRefreshStorageFailure() {
        // given
        stubValidMember();
        given(attempts.clearIfUnlocked("MEMBER", request.email())).willReturn(true);
        JwtTokenPair pair = new JwtTokenPair("access", "refresh", "id", Duration.ofMinutes(30), Duration.ofDays(14));
        given(tokens.createMemberTokens(1L)).willReturn(pair);
        doThrow(new DataAccessResourceFailureException("Redis unavailable")).when(refreshTokens)
                .save("MEMBER", 1L, "id", "refresh", Duration.ofDays(14));

        // when
        Throwable thrown = catchThrowable(() -> service.login(request));

        // then
        assertThat(thrown).isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    @DisplayName("Redis 잠금 조회에 실패하면 회원 조회와 토큰 발급을 진행하지 않는다")
    void stopsWhenRedisIsUnavailable() {
        // given
        given(attempts.isLocked("MEMBER", request.email()))
                .willThrow(new DataAccessResourceFailureException("Redis unavailable"));

        // when
        Throwable thrown = catchThrowable(() -> service.login(request));

        // then
        assertThat(thrown).isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(members, tokens, refreshTokens);
    }

    private void stubValidMember() {
        Member member = Member.builder().email(request.email()).passwordHash("stored-hash").build();
        ReflectionTestUtils.setField(member, "id", 1L);
        given(members.findByEmailIgnoreCase(request.email())).willReturn(Optional.of(member));
        given(encoder.matches(request.password(), "stored-hash")).willReturn(true);
    }
}
