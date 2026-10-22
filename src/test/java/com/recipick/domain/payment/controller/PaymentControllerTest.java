package com.recipick.domain.payment.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recipick.domain.payment.dto.response.PaymentConfirmResponse;
import com.recipick.domain.payment.dto.response.PaymentDetailResponse;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.facade.PaymentConfirmFacade;
import com.recipick.domain.payment.facade.PaymentQueryFacade;
import com.recipick.global.config.ClockConfig;
import com.recipick.global.config.JacksonConfig;
import com.recipick.global.config.JwtConfig;
import com.recipick.global.config.SecurityConfig;
import com.recipick.global.config.WebConfig;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.GlobalExceptionHandler;
import com.recipick.global.security.JwtAccessDeniedHandler;
import com.recipick.global.security.JwtAuthenticationEntryPoint;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.LoginPrincipalArgumentResolver;
import com.recipick.global.security.SecurityErrorResponseWriter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// 보안 설정 구성은 AuthenticationWebTest(채빈)와 같다. 컨트롤러 한 장만 올리고 Facade는 mock이라 Docker가 필요 없다
@WebMvcTest(controllers = PaymentController.class,
    properties = "security.cors.allowed-origins=http://localhost:3000")
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtConfig.class, JwtTokenProvider.class, ClockConfig.class,
    WebConfig.class, LoginPrincipalArgumentResolver.class, SecurityErrorResponseWriter.class,
    JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JacksonConfig.class,
    GlobalExceptionHandler.class})
class PaymentControllerTest {

    private static final String SECRET = Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());
    private static final Long MEMBER_ID = 7L;
    private static final String CONFIRM_BODY = """
            {"orderId": 1, "portonePaymentId": "pay_1"}
            """;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockitoBean
    private PaymentConfirmFacade paymentConfirmFacade;

    @MockitoBean
    private PaymentQueryFacade paymentQueryFacade;

    @Test
    @DisplayName("회원 토큰과 올바른 본문이면 토큰의 회원 ID로 Facade를 부르고 200 성공 봉투에 담아 돌려준다")
    void confirm() throws Exception {
        // given
        given(paymentConfirmFacade.confirm(MEMBER_ID, 1L, "pay_1")).willReturn(new PaymentConfirmResponse(
            10L, 1L, PaymentMethod.CARD, 23800L, PaymentStatus.PAID, LocalDateTime.of(2026, 10, 21, 10, 3)));

        // when
        ResultActions result = mockMvc.perform(post("/api/payments/confirm")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(CONFIRM_BODY));

        // then
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.message").value("결제가 확정되었습니다."))
            .andExpect(jsonPath("$.data.paymentId").value(10))
            .andExpect(jsonPath("$.data.orderId").value(1))
            .andExpect(jsonPath("$.data.paymentMethod").value("CARD"))
            .andExpect(jsonPath("$.data.amount").value(23800))
            .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
            .andExpect(jsonPath("$.data.paidAt").value("2026-10-21T10:03:00+09:00"));
        verify(paymentConfirmFacade).confirm(MEMBER_ID, 1L, "pay_1");
    }

    @Test
    @DisplayName("토큰이 없으면 AUTH-001이고 Facade는 부르지 않는다")
    void confirmWithoutToken() throws Exception {
        // when
        ResultActions result = mockMvc.perform(post("/api/payments/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content(CONFIRM_BODY));

        // then
        result.andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("AUTH-001"));
        verify(paymentConfirmFacade, never()).confirm(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("본문 검증에 실패하면 COMMON-001과 필드 오류 목록을 돌려주고 Facade는 부르지 않는다")
    void confirmWithInvalidBody() throws Exception {
        // when
        ResultActions result = mockMvc.perform(post("/api/payments/confirm")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"orderId\": 1, \"portonePaymentId\": \"\"}"));

        // then
        result.andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COMMON-001"))
            .andExpect(jsonPath("$.errors[0].field").value("portonePaymentId"));
        verify(paymentConfirmFacade, never()).confirm(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("Facade가 던진 결제 오류는 그 코드 그대로 오류 봉투로 나간다")
    void confirmWithPaymentError() throws Exception {
        // given
        given(paymentConfirmFacade.confirm(anyLong(), anyLong(), anyString()))
            .willThrow(new BusinessException(PaymentErrorCode.PAYMENT_EXPIRED));

        // when
        ResultActions result = mockMvc.perform(post("/api/payments/confirm")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(CONFIRM_BODY));

        // then
        result.andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("PAYMENT-005"))
            .andExpect(jsonPath("$.message").value("결제 가능 시간이 만료되었습니다."));
    }

    @Test
    @DisplayName("회원 토큰으로 주문 결제를 조회하면 토큰의 회원 ID로 Facade를 부르고 결제 정보를 돌려준다")
    void getByOrderId() throws Exception {
        // given
        given(paymentQueryFacade.getByOrderId(MEMBER_ID, 1L)).willReturn(new PaymentDetailResponse(
            10L, 1L, "pay_1", PaymentMethod.CARD, 23800L, PaymentStatus.IN_PROGRESS,
            null, LocalDateTime.of(2026, 10, 22, 10, 5)));

        // when
        ResultActions result = mockMvc.perform(get("/api/payments/orders/{orderId}", 1L)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken()));

        // then
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.message").value("요청이 처리되었습니다."))
            .andExpect(jsonPath("$.data.paymentId").value(10))
            .andExpect(jsonPath("$.data.orderId").value(1))
            .andExpect(jsonPath("$.data.portonePaymentId").value("pay_1"))
            .andExpect(jsonPath("$.data.paymentMethod").value("CARD"))
            .andExpect(jsonPath("$.data.amount").value(23800))
            .andExpect(jsonPath("$.data.paymentStatus").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.paidAt").value(nullValue()))
            .andExpect(jsonPath("$.data.expiresAt").value("2026-10-22T10:05:00+09:00"))
            .andExpect(jsonPath("$.data.failureReason").doesNotExist());
        verify(paymentQueryFacade).getByOrderId(MEMBER_ID, 1L);
    }

    @Test
    @DisplayName("토큰이 없으면 주문 결제를 조회할 수 없고 AUTH-001을 반환한다")
    void getByOrderIdWithoutToken() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/api/payments/orders/{orderId}", 1L));

        // then
        result.andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("AUTH-001"));
        verify(paymentQueryFacade, never()).getByOrderId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Facade가 PAYMENT-001을 던지면 404 오류 봉투로 반환한다")
    void getByOrderIdWithPaymentError() throws Exception {
        // given
        given(paymentQueryFacade.getByOrderId(anyLong(), anyLong()))
            .willThrow(new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        // when
        ResultActions result = mockMvc.perform(get("/api/payments/orders/{orderId}", 1L)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken()));

        // then
        result.andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("PAYMENT-001"))
            .andExpect(jsonPath("$.message").value("결제 정보를 찾을 수 없습니다."));
    }

    private String memberToken() {
        return tokenProvider.createMemberTokens(MEMBER_ID).accessToken();
    }
}
