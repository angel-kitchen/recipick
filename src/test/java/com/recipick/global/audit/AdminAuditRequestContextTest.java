package com.recipick.global.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.SubjectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AdminAuditRequestContextTest {

    private final AdminAuditRequestContext context = new AdminAuditRequestContext();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("관리자 ID는 요청 값이 아닌 인증된 Principal에서 가져온다")
    void usesAuthenticatedAdmin() {
        // given
        AuthPrincipal principal = new AuthPrincipal(SubjectType.ADMIN, 7L, "SUPER_ADMIN", null);
        authenticate(principal);

        // when
        AuthPrincipal result = context.requireAdmin();

        // then
        assertThat(result.id()).isEqualTo(7L);
    }

    @Test
    @DisplayName("인증이 없거나 일반 회원이면 관리자 감사 기록 주체가 될 수 없다")
    void rejectsAnonymousAndMember() {
        // given
        SecurityContextHolder.clearContext();

        // when
        var anonymous = assertThatThrownBy(context::requireAdmin);

        // then
        anonymous.isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.LOGIN_REQUIRED));

        // given
        authenticate(new AuthPrincipal(SubjectType.MEMBER, 1L, null, null));

        // when
        var member = assertThatThrownBy(context::requireAdmin);

        // then
        member.isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("위조한 전달 헤더를 무시하고 서버가 확인한 직접 접속 주소를 사용한다")
    void ignoresForwardedHeaders() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.7");
        request.addHeader("X-Forwarded-For", "203.0.113.1");
        request.addHeader("X-Real-IP", "203.0.113.2");
        request.addHeader("Forwarded", "for=203.0.113.3");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // when
        String result = context.clientIp();

        // then
        assertThat(result).isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("IPv6 접속 주소도 저장할 수 있으며 요청이 없으면 IP는 비워둔다")
    void supportsIpv6AndMissingRequest() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("2001:db8::1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // when
        String result = context.clientIp();
        RequestContextHolder.resetRequestAttributes();
        String missing = context.clientIp();

        // then
        assertThat(result).isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThat(missing).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "example.com", "999.1.1.1", "256.0.0.1", "01.2.3.4", "127.1", "::::", "127.0.0.1:8080",
            "2001::db8::1", "1:2:3:4:5:6:7:8:9", "1:2:3:4:5:6:7::8", "::ffff:999.1.1.1",
            "fe80::1%eth0", "[::1]", "", "1:2:3:4:5:6:7", "12345::1", "::1:"})
    @DisplayName("호스트 이름이나 잘못된 주소는 DNS로 조회하지 않고 기록에서 제외한다")
    void ignoresInvalidAddress(String address) {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // when
        String result = context.clientIp();

        // then
        assertThat(result).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "0.0.0.0, 0.0.0.0",
            "255.255.255.255, 255.255.255.255",
            "::, 0:0:0:0:0:0:0:0",
            "::1, 0:0:0:0:0:0:0:1",
            "2001:DB8:0:0:0:0:0:1, 2001:db8:0:0:0:0:0:1",
            "1:2:3:4:5:6:7:8, 1:2:3:4:5:6:7:8",
            "::ffff:192.0.2.1, 192.0.2.1",
            "2001:db8::192.0.2.1, 2001:db8:0:0:0:0:c000:201"
    })
    @DisplayName("정상 IPv4와 압축·혼합 IPv6 주소를 DNS 없이 정규화한다")
    void normalizesNumericAddresses(String address, String expected) {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // when
        String result = context.clientIp();

        // then
        assertThat(result).isEqualTo(expected);
    }

    private void authenticate(AuthPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.authorities()));
    }
}
