package com.recipick.domain.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recipick.domain.payment.dto.response.PointBalanceResponse;
import com.recipick.domain.payment.facade.PointAdminQueryFacade;
import com.recipick.global.config.ClockConfig;
import com.recipick.global.config.JacksonConfig;
import com.recipick.global.config.JwtConfig;
import com.recipick.global.config.SecurityConfig;
import com.recipick.global.config.WebConfig;
import com.recipick.global.exception.GlobalExceptionHandler;
import com.recipick.global.response.PageResponse;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtAccessDeniedHandler;
import com.recipick.global.security.JwtAuthenticationEntryPoint;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.LoginPrincipalArgumentResolver;
import com.recipick.global.security.MemberStatusChecker;
import com.recipick.global.security.SecurityErrorResponseWriter;
import com.recipick.global.security.SubjectType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// 보안 설정 구성은 PaymentControllerTest와 같다. Facade는 mock이라 감사 기록은 PointAdminQueryFacadeTest·통합 테스트에서 본다
@WebMvcTest(controllers = PointAdminController.class,
    properties = "security.cors.allowed-origins=http://localhost:3000")
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtConfig.class, JwtTokenProvider.class, ClockConfig.class,
    WebConfig.class, LoginPrincipalArgumentResolver.class, SecurityErrorResponseWriter.class,
    JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JacksonConfig.class,
    GlobalExceptionHandler.class})
class PointAdminControllerTest {

    private static final String SECRET = Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());
    private static final Long MEMBER_ID = 7L;
    private static final Long ADMIN_ID = 1L;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockitoBean
    private MemberStatusChecker memberStatusChecker;

    @MockitoBean
    private PointAdminQueryFacade pointAdminQueryFacade;

    @Test
    @DisplayName("SUPER_ADMIN은 경로의 회원 ID로 그 회원의 사용 가능 잔액과 가까운 만료를 조회한다")
    void getBalance() throws Exception {
        // given
        given(pointAdminQueryFacade.getBalance(MEMBER_ID))
            .willReturn(new PointBalanceResponse(MEMBER_ID, 500L, 500L, LocalDate.of(2027, 1, 1)));

        // when
        ResultActions result = perform("/api/admin/members/{memberId}/points", MEMBER_ID, superAdminToken());

        // then
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.data.memberId").value(7))
            .andExpect(jsonPath("$.data.availableBalance").value(500));
        verify(pointAdminQueryFacade).getBalance(MEMBER_ID);
    }

    @Test
    @DisplayName("SUPER_ADMIN의 이력 조회는 회원 ID·주문 ID를 그대로 Facade에 넘긴다")
    void findHistory() throws Exception {
        // given
        given(pointAdminQueryFacade.findHistory(eq(MEMBER_ID), isNull(), any(Pageable.class)))
            .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        // when
        ResultActions result = perform("/api/admin/members/{memberId}/points/history", MEMBER_ID, superAdminToken());

        // then
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(0));
        verify(pointAdminQueryFacade).findHistory(eq(MEMBER_ID), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("회원 ID가 0 이하면 조회·감사 전에 COMMON-001로 끊고 Facade를 부르지 않는다")
    void getBalanceWithNonPositiveMemberId() throws Exception {
        // when
        ResultActions balance = perform("/api/admin/members/{memberId}/points", 0L, superAdminToken());
        ResultActions history = perform("/api/admin/members/{memberId}/points/history", -1L, superAdminToken());

        // then
        balance.andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-001"));
        history.andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-001"));
        verify(pointAdminQueryFacade, never()).getBalance(anyLong());
        verify(pointAdminQueryFacade, never()).findHistory(anyLong(), any(), any());
    }

    @Test
    @DisplayName("이력의 주문 ID가 0 이하면 COMMON-001이고 Facade를 부르지 않는다")
    void findHistoryWithNonPositiveOrderId() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/api/admin/members/{memberId}/points/history", MEMBER_ID)
            .param("orderId", "0")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdminToken()));

        // then
        result.andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-001"));
        verify(pointAdminQueryFacade, never()).findHistory(anyLong(), any(), any());
    }

    @Test
    @DisplayName("MART_ADMIN은 포인트 조회가 SUPER_ADMIN 전용이라 AUTH-006이고 Facade를 부르지 않는다")
    void martAdminIsDenied() throws Exception {
        // given
        String martAdminToken = tokenProvider.createTokens(
            new AuthPrincipal(SubjectType.ADMIN, ADMIN_ID, "MART_ADMIN", 1L)).accessToken();

        // when
        ResultActions result = perform("/api/admin/members/{memberId}/points", MEMBER_ID, martAdminToken);

        // then
        result.andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTH-006"));
        verify(pointAdminQueryFacade, never()).getBalance(anyLong());
    }

    @Test
    @DisplayName("회원 토큰으로는 관리자 포인트 API를 부를 수 없고 AUTH-004를 반환한다")
    void memberIsDenied() throws Exception {
        // when
        ResultActions result = perform("/api/admin/members/{memberId}/points", MEMBER_ID,
            tokenProvider.createMemberTokens(MEMBER_ID).accessToken());

        // then
        result.andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTH-004"));
        verify(pointAdminQueryFacade, never()).getBalance(anyLong());
    }

    private ResultActions perform(String path, Long memberId, String token) throws Exception {
        return mockMvc.perform(get(path, memberId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String superAdminToken() {
        return tokenProvider.createTokens(new AuthPrincipal(SubjectType.ADMIN, ADMIN_ID, "SUPER_ADMIN", null))
            .accessToken();
    }
}
