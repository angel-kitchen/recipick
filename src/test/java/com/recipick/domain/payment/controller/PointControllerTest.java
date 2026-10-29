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
import com.recipick.domain.payment.dto.response.PointEarningResponse;
import com.recipick.domain.payment.dto.response.PointHistoryResponse;
import com.recipick.domain.payment.entity.enums.PointEarningStatus;
import com.recipick.domain.payment.entity.enums.PointHistoryType;
import com.recipick.domain.payment.service.PointQueryService;
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
import java.time.LocalDateTime;
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

// 보안 설정 구성은 PaymentControllerTest와 같다. 컨트롤러 한 장만 올리고 조회 Service는 mock이라 Docker가 필요 없다
@WebMvcTest(controllers = PointController.class,
    properties = "security.cors.allowed-origins=http://localhost:3000")
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtConfig.class, JwtTokenProvider.class, ClockConfig.class,
    WebConfig.class, LoginPrincipalArgumentResolver.class, SecurityErrorResponseWriter.class,
    JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JacksonConfig.class,
    GlobalExceptionHandler.class})
class PointControllerTest {

    private static final String SECRET = Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());
    private static final Long MEMBER_ID = 7L;
    private static final Long ORDER_ID = 1001L;

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
    private PointQueryService pointQueryService;

    @Test
    @DisplayName("회원 토큰으로 잔액을 조회하면 토큰의 회원 ID로 조회해 사용 가능 잔액과 가까운 만료를 돌려준다")
    void getBalance() throws Exception {
        // given
        given(pointQueryService.getBalance(MEMBER_ID))
            .willReturn(new PointBalanceResponse(MEMBER_ID, 8_500L, 2_000L, LocalDate.of(2026, 12, 31)));

        // when
        ResultActions result = mockMvc.perform(get("/api/points").header(HttpHeaders.AUTHORIZATION, memberBearer()));

        // then
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("포인트를 조회했습니다."))
            .andExpect(jsonPath("$.data.memberId").value(7))
            .andExpect(jsonPath("$.data.availableBalance").value(8500))
            .andExpect(jsonPath("$.data.nearestExpiringAmount").value(2000))
            .andExpect(jsonPath("$.data.nearestExpiresOn").value("2026-12-31"));
    }

    @Test
    @DisplayName("적립 내역은 토큰의 회원 ID와 페이지 정보로 조회하고 날짜를 반영한 사용 가능 잔량을 돌려준다")
    void findEarnings() throws Exception {
        // given
        PointEarningResponse row = new PointEarningResponse(51L, ORDER_ID, PointEarningStatus.COMPLETED, 500L, 0L,
            LocalDateTime.of(2025, 10, 27, 10, 0), LocalDate.of(2026, 10, 27), false,
            LocalDateTime.of(2025, 10, 20, 9, 0));
        given(pointQueryService.findEarnings(eq(MEMBER_ID), any(Pageable.class)))
            .willReturn(new PageResponse<>(List.of(row), 0, 20, 1, 1));

        // when
        ResultActions result = mockMvc.perform(get("/api/points/earnings")
            .header(HttpHeaders.AUTHORIZATION, memberBearer()));

        // then
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].earningId").value(51))
            .andExpect(jsonPath("$.data.content[0].availableRemainingAmount").value(0))
            .andExpect(jsonPath("$.data.content[0].usable").value(false))
            .andExpect(jsonPath("$.data.content[0].expiresOn").value("2026-10-27"));
    }

    @Test
    @DisplayName("이력은 주문 ID를 보내면 그 값으로, 보내지 않으면 null로 조회 Service에 넘긴다")
    void findHistory() throws Exception {
        // given
        PointHistoryResponse row = new PointHistoryResponse(77L, 51L, ORDER_ID, PointHistoryType.USE_RESERVED, 300L,
            LocalDateTime.of(2026, 10, 28, 14, 0));
        given(pointQueryService.findHistory(eq(MEMBER_ID), eq(ORDER_ID), any(Pageable.class)))
            .willReturn(new PageResponse<>(List.of(row), 0, 20, 1, 1));
        given(pointQueryService.findHistory(eq(MEMBER_ID), isNull(), any(Pageable.class)))
            .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        // when
        ResultActions byOrder = mockMvc.perform(get("/api/points/history").param("orderId", "1001")
            .header(HttpHeaders.AUTHORIZATION, memberBearer()));
        ResultActions all = mockMvc.perform(get("/api/points/history")
            .header(HttpHeaders.AUTHORIZATION, memberBearer()));

        // then
        byOrder.andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].historyId").value(77))
            .andExpect(jsonPath("$.data.content[0].earningId").value(51))
            .andExpect(jsonPath("$.data.content[0].type").value("USE_RESERVED"))
            .andExpect(jsonPath("$.data.content[0].amount").value(300));
        all.andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("주문 ID가 0 이하면 COMMON-001이고 조회하지 않는다")
    void findHistoryWithNonPositiveOrderId() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/api/points/history").param("orderId", "0")
            .header(HttpHeaders.AUTHORIZATION, memberBearer()));

        // then
        result.andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-001"));
        verify(pointQueryService, never()).findHistory(anyLong(), any(), any());
    }

    @Test
    @DisplayName("주문 ID가 숫자가 아니면 COMMON-002이고 조회하지 않는다")
    void findHistoryWithNonNumericOrderId() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/api/points/history").param("orderId", "abc")
            .header(HttpHeaders.AUTHORIZATION, memberBearer()));

        // then
        result.andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-002"));
        verify(pointQueryService, never()).findHistory(anyLong(), any(), any());
    }

    @Test
    @DisplayName("관리자 토큰으로는 회원 포인트 API를 부를 수 없고 AUTH-004를 반환한다")
    void getBalanceWithAdminToken() throws Exception {
        // given
        String adminToken = tokenProvider.createTokens(
            new AuthPrincipal(SubjectType.ADMIN, 1L, "SUPER_ADMIN", null)).accessToken();

        // when
        ResultActions result = mockMvc.perform(get("/api/points")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken));

        // then
        result.andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTH-004"));
        verify(pointQueryService, never()).getBalance(anyLong());
    }

    private String memberBearer() {
        return "Bearer " + tokenProvider.createMemberTokens(MEMBER_ID).accessToken();
    }
}
