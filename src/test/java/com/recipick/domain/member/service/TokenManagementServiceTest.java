package com.recipick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenPair;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.RefreshTokenDetails;
import com.recipick.global.security.SubjectType;
import com.recipick.infra.redis.RedisRefreshTokenStore;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class TokenManagementServiceTest {

    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private RedisRefreshTokenStore store;
    @Mock
    private MemberRepository members;
    @Mock
    private AdminRepository admins;
    @InjectMocks
    private TokenManagementService service;

    @Test
    @DisplayName("Redis 토큰 교체에 실패하면 새 토큰을 성공 결과로 반환하지 않는다")
    void propagatesRotationFailure() {
        // given
        AuthPrincipal principal = new AuthPrincipal(SubjectType.MEMBER, 1L, null, null, "session-id");
        JwtTokenPair pair = new JwtTokenPair("access", "new-refresh", "new-id",
                Duration.ofMinutes(30), Duration.ofDays(14));
        given(tokenProvider.parseRefreshToken("old-refresh"))
                .willReturn(new RefreshTokenDetails(principal, "old-id", "session-id"));
        given(members.existsById(1L)).willReturn(true);
        given(tokenProvider.rotateTokens(principal, "session-id")).willReturn(pair);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Redis unavailable");
        given(store.rotate("MEMBER", 1L, "session-id", "old-refresh", "new-refresh", Duration.ofDays(14)))
                .willThrow(failure);

        // when
        Throwable thrown = catchThrowable(() -> service.reissue("old-refresh"));

        // then
        assertThat(thrown).isSameAs(failure);
    }

    @Test
    @DisplayName("Redis 세션 삭제에 실패하면 로그아웃을 성공으로 처리하지 않는다")
    void propagatesLogoutFailure() {
        // given
        AuthPrincipal principal = new AuthPrincipal(SubjectType.MEMBER, 1L, null, null, "session-id");
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("Redis unavailable");
        doThrow(failure).when(store).delete("MEMBER", 1L, "session-id");

        // when
        Throwable thrown = catchThrowable(() -> service.logout(principal, null));

        // then
        assertThat(thrown).isSameAs(failure);
        verifyNoInteractions(tokenProvider, members, admins);
    }
}
