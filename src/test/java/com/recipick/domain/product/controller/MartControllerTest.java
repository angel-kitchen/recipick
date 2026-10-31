package com.recipick.domain.product.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recipick.domain.product.dto.MartMapResponse;
import com.recipick.domain.product.entity.MartStatus;
import com.recipick.domain.product.service.MartService;
import com.recipick.global.config.ClockConfig;
import com.recipick.global.config.JacksonConfig;
import com.recipick.global.config.JwtConfig;
import com.recipick.global.config.WebConfig;
import com.recipick.global.exception.GlobalExceptionHandler;
import com.recipick.global.security.JwtAccessDeniedHandler;
import com.recipick.global.security.JwtAuthenticationEntryPoint;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.LoginPrincipalArgumentResolver;
import com.recipick.global.security.MemberStatusChecker;
import com.recipick.global.security.SecurityErrorResponseWriter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.recipick.global.config.SecurityConfig;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;


@WebMvcTest(
    controllers = MartController.class,
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
class MartControllerTest {

    private static final String SECRET =
        Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberStatusChecker memberStatusChecker;

    @MockitoBean
    private MartService martService;

    @Test
    @DisplayName("토큰 없이 마트 공개 목록을 조회하면 위치와 반경을 반환한다")
    void getMarts() throws Exception {
        // given
        given(martService.findOpenMartsForMap()).willReturn(List.of(
            new MartMapResponse(1L, "삼청 마트", new BigDecimal("37.5860000"),
                new BigDecimal("126.9820000"), 2000, MartStatus.OPEN)));

        // when & then
        mockMvc.perform(get("/api/config/marts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].martId").value(1))
            .andExpect(jsonPath("$.data[0].name").value("삼청 마트"))
            .andExpect(jsonPath("$.data[0].latitude").value(37.586))
            .andExpect(jsonPath("$.data[0].serviceRadiusM").value(2000))
            .andExpect(jsonPath("$.data[0].regionId").doesNotExist());
    }
}
