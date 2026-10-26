package com.recipick.domain.payment.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.facade.PortOneWebhookFacade;
import com.recipick.domain.payment.service.WebhookReceiveOutcome;
import com.recipick.global.config.ClockConfig;
import com.recipick.global.config.JacksonConfig;
import com.recipick.global.config.JwtConfig;
import com.recipick.global.config.SecurityConfig;
import com.recipick.global.config.WebConfig;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.GlobalExceptionHandler;
import com.recipick.global.security.JwtAccessDeniedHandler;
import com.recipick.global.security.JwtAuthenticationEntryPoint;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.LoginPrincipalArgumentResolver;
import com.recipick.global.security.SecurityErrorResponseWriter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// 보안 설정 구성은 PaymentControllerTest와 같다. 웹훅은 permitAll이라 토큰 없이 부른다
@WebMvcTest(controllers = PortOneWebhookController.class,
    properties = "security.cors.allowed-origins=http://localhost:3000")
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtConfig.class, JwtTokenProvider.class, ClockConfig.class,
    WebConfig.class, LoginPrincipalArgumentResolver.class, SecurityErrorResponseWriter.class,
    JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JacksonConfig.class,
    GlobalExceptionHandler.class})
class PortOneWebhookControllerTest {

    private static final String SECRET = Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());
    private static final String WEBHOOK_ID = "msg_1";
    private static final String TIMESTAMP = "1761393600";
    private static final String SIGNATURE = "v1,abc";
    // 공백·줄바꿈·한글이 섞인 원문. 바인딩 없이 그대로 Facade에 닿아야 서명이 유지된다
    private static final String BODY = """
            { "type": "Transaction.Paid",
              "data": { "paymentId": "pay_1", "memo": "단순 변심" } }
            """;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortOneWebhookFacade portOneWebhookFacade;

    @Test
    @DisplayName("토큰 없이 헤더 셋과 원문을 그대로 Facade에 넘기고 200 성공 봉투를 돌려준다")
    void receive() throws Exception {
        // given
        given(portOneWebhookFacade.receive(WEBHOOK_ID, TIMESTAMP, SIGNATURE, BODY))
            .willReturn(WebhookReceiveOutcome.RECEIVED);

        // when
        ResultActions result = mockMvc.perform(post("/api/webhooks/portone")
            .header("webhook-id", WEBHOOK_ID)
            .header("webhook-timestamp", TIMESTAMP)
            .header("webhook-signature", SIGNATURE)
            .contentType(MediaType.APPLICATION_JSON)
            .content(BODY));

        // then
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("OK"));
        verify(portOneWebhookFacade).receive(WEBHOOK_ID, TIMESTAMP, SIGNATURE, BODY);
    }

    @Test
    @DisplayName("헤더가 없으면 null로 넘긴다 — 누락 판정은 Facade의 서명 검증 하나로 모은다")
    void receiveWithoutHeaders() throws Exception {
        // given
        given(portOneWebhookFacade.receive(isNull(), isNull(), isNull(), anyString()))
            .willThrow(new BusinessException(AuthErrorCode.INVALID_WEBHOOK_SIGNATURE));

        // when
        ResultActions result = mockMvc.perform(post("/api/webhooks/portone")
            .contentType(MediaType.APPLICATION_JSON)
            .content(BODY));

        // then
        result.andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("AUTH-010"));
    }

    @Test
    @DisplayName("Facade가 PAYMENT-011을 던지면 500 오류 봉투다 — 시크릿 미설정은 우리 쪽 문제라 PortOne이 재전송한다")
    void receiveWhenNotConfigured() throws Exception {
        // given
        given(portOneWebhookFacade.receive(anyString(), anyString(), anyString(), anyString()))
            .willThrow(new BusinessException(PaymentErrorCode.WEBHOOK_NOT_CONFIGURED));

        // when
        ResultActions result = mockMvc.perform(post("/api/webhooks/portone")
            .header("webhook-id", WEBHOOK_ID)
            .header("webhook-timestamp", TIMESTAMP)
            .header("webhook-signature", SIGNATURE)
            .contentType(MediaType.APPLICATION_JSON)
            .content(BODY));

        // then
        result.andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("PAYMENT-011"));
    }

    @Test
    @DisplayName("본문이 없으면 400 COMMON-002고 Facade를 부르지 않는다")
    void receiveWithoutBody() throws Exception {
        // when
        ResultActions result = mockMvc.perform(post("/api/webhooks/portone")
            .header("webhook-id", WEBHOOK_ID)
            .header("webhook-timestamp", TIMESTAMP)
            .header("webhook-signature", SIGNATURE)
            .contentType(MediaType.APPLICATION_JSON));

        // then
        result.andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-002"));
        verifyNoInteractions(portOneWebhookFacade);
    }
}
