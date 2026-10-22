package com.recipick.domain.payment.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recipick.domain.payment.dto.response.PortOneConfigResponse;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.PortOneConfigService;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = PortOneConfigController.class,
    properties = "security.cors.allowed-origins=http://localhost:3000"
)
@ActiveProfiles("test")
@Import({
    SecurityConfig.class,
    JwtConfig.class,
    JwtTokenProvider.class,
    ClockConfig.class,
    WebConfig.class,
    LoginPrincipalArgumentResolver.class,
    SecurityErrorResponseWriter.class,
    JwtAuthenticationEntryPoint.class,
    JwtAccessDeniedHandler.class,
    JacksonConfig.class,
    GlobalExceptionHandler.class
})
class PortOneConfigControllerTest {

    private static final String SECRET =
        Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortOneConfigService portOneConfigService;

    @Test
    @DisplayName("토큰 없이 PortOne 공개 설정을 조회하면 storeId와 channelKey를 반환한다")
    void getPublicConfig() throws Exception {
        // given
        given(portOneConfigService.getPublicConfig())
            .willReturn(new PortOneConfigResponse(
                "test-store-id",
                "test-channel-key"
            ));

        // when, then
        mockMvc.perform(get("/api/config/portone"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.storeId").value("test-store-id"))
            .andExpect(jsonPath("$.data.channelKey").value("test-channel-key"))
            .andExpect(jsonPath("$.data.apiSecret").doesNotExist());

        verify(portOneConfigService).getPublicConfig();
    }

    @Test
    @DisplayName("공개 설정이 없으면 PAYMENT-010을 반환한다")
    void getPublicConfigWithMissingConfig() throws Exception {
        // given
        given(portOneConfigService.getPublicConfig())
            .willThrow(new BusinessException(
                PaymentErrorCode.PORTONE_CONFIG_ERROR
            ));

        // when, then
        mockMvc.perform(get("/api/config/portone"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("PAYMENT-010"))
            .andExpect(jsonPath("$.message")
                .value("결제 공개 설정을 불러올 수 없습니다."));
    }
}
