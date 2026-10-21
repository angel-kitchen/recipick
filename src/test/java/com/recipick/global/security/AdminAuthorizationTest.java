package com.recipick.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminAuthorizationTest {

    private final AdminAuthorization authorization = new AdminAuthorization();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("슈퍼 관리자는 모든 마트에 접근할 수 있다")
    void allowsSuperAdmin() {
        // given
        authenticate(new AuthPrincipal(SubjectType.ADMIN, 1L, "SUPER_ADMIN", null));

        // when
        Throwable first = catchThrowable(() -> authorization.checkMartAccess(1L));
        Throwable second = catchThrowable(() -> authorization.checkMartAccess(2L));

        // then
        assertThat(first).isNull();
        assertThat(second).isNull();
    }

    @Test
    @DisplayName("마트 관리자는 자기 마트만 접근할 수 있다")
    void restrictsMartAdmin() {
        // given
        authenticate(new AuthPrincipal(SubjectType.ADMIN, 2L, "MART_ADMIN", 1L));

        // when
        Throwable own = catchThrowable(() -> authorization.checkMartAccess(1L));
        Throwable other = catchThrowable(() -> authorization.checkMartAccess(2L));

        // then
        assertThat(own).isNull();
        assertError(other, AuthErrorCode.MART_ACCESS_DENIED);
    }

    @Test
    @DisplayName("인증이 없으면 로그인 필요 오류다")
    void rejectsUnauthenticatedRequest() {
        // given
        SecurityContextHolder.clearContext();

        // when
        Throwable thrown = catchThrowable(() -> authorization.checkMartAccess(1L));

        // then
        assertError(thrown, AuthErrorCode.LOGIN_REQUIRED);
    }

    @Test
    @DisplayName("일반 회원에게 관리자 마트 접근 권한을 주지 않는다")
    void rejectsMember() {
        // given
        authenticate(new AuthPrincipal(SubjectType.MEMBER, 1L, null, null));

        // when
        Throwable thrown = catchThrowable(() -> authorization.checkMartAccess(1L));

        // then
        assertError(thrown, AuthErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("관리자여도 대상 마트 ID가 없거나 양수가 아니면 입력 오류다")
    void validatesTargetMartId() {
        // given
        authenticate(new AuthPrincipal(SubjectType.ADMIN, 1L, "SUPER_ADMIN", null));

        // when
        Throwable missing = catchThrowable(() -> authorization.checkMartAccess(null));
        Throwable invalid = catchThrowable(() -> authorization.checkMartAccess(0L));

        // then
        assertError(missing, CommonErrorCode.INVALID_INPUT);
        assertError(invalid, CommonErrorCode.INVALID_INPUT);
    }

    private void authenticate(AuthPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.authorities()));
    }

    private void assertError(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
