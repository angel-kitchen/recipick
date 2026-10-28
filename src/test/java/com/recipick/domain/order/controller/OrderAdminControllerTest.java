package com.recipick.domain.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.order.facade.OrderQueryFacade;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.MartRejectionPaymentResult;
import com.recipick.domain.payment.dto.response.MartRejectionResponse;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.facade.MartRejectionFacade;
import com.recipick.domain.payment.service.MartRejectionOutcome;
import com.recipick.global.config.ClockConfig;
import com.recipick.global.config.JacksonConfig;
import com.recipick.global.config.JwtConfig;
import com.recipick.global.config.SecurityConfig;
import com.recipick.global.config.WebConfig;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.GlobalExceptionHandler;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 보안 설정 구성은 PaymentControllerTest와 같다. 컨트롤러 한 장만 올리고 Service·Facade는 mock이라 Docker가 필요 없다
@WebMvcTest(controllers = OrderAdminController.class,
    properties = "security.cors.allowed-origins=http://localhost:3000")
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtConfig.class, JwtTokenProvider.class, ClockConfig.class,
    WebConfig.class, LoginPrincipalArgumentResolver.class, SecurityErrorResponseWriter.class,
    JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, JacksonConfig.class,
    GlobalExceptionHandler.class})
class OrderAdminControllerTest {

    private static final String SECRET = Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());
    private static final Long ORDER_ID = 1L;
    private static final Long MART_ID = 3L;
    private static final Long ADMIN_ID = 17L;
    private static final String KEY = "admin-key-1";
    private static final String REASON = "상품 준비가 어렵습니다.";
    private static final String REJECT_BODY = """
            {"reason": "상품 준비가 어렵습니다."}
            """;
    private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 10, 28, 14, 0, 40);

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
    private OrderService orderService;

    @MockitoBean
    private OrderQueryFacade orderQueryFacade;

    @MockitoBean
    private MartRejectionFacade martRejectionFacade;

    @Test
    @DisplayName("마트 관리자 토큰으로 거절이 끝나면 토큰의 역할·마트·관리자 ID로 Facade를 부르고 200과 취소 완료 정보를 돌려준다")
    void rejectCompleted() throws Exception {
        // given
        given(martRejectionFacade.reject(AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID, KEY, REASON))
            .willReturn(new MartRejectionPaymentResult(MartRejectionOutcome.COMPLETED,
                new MartRejectionResponse(ORDER_ID, 10L, PaymentStatus.CANCELED, CANCELED_AT, 77L,
                    RefundStatus.COMPLETED, 23800L)));

        // when
        ResultActions result = perform(martAdminToken(), KEY, REJECT_BODY);

        // then
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("주문을 거절하고 결제를 취소했습니다."))
            .andExpect(jsonPath("$.data.orderId").value(1))
            .andExpect(jsonPath("$.data.paymentStatus").value("CANCELED"))
            .andExpect(jsonPath("$.data.canceledAt").value("2026-10-28T14:00:40+09:00"))
            .andExpect(jsonPath("$.data.refundId").value(77))
            .andExpect(jsonPath("$.data.refundStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.data.refundAmount").value(23800));
        verify(martRejectionFacade).reject(AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID, KEY, REASON);
    }

    @Test
    @DisplayName("PortOne 결과를 아직 확정할 수 없으면 202와 확인 중 메시지, 취소 요청 상태를 돌려준다")
    void rejectPending() throws Exception {
        // given
        given(martRejectionFacade.reject(AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID, KEY, REASON))
            .willReturn(new MartRejectionPaymentResult(MartRejectionOutcome.PENDING,
                new MartRejectionResponse(ORDER_ID, 10L, PaymentStatus.CANCEL_REQUESTED, null, 77L,
                    RefundStatus.REQUESTED, 23800L)));

        // when
        ResultActions result = perform(martAdminToken(), KEY, REJECT_BODY);

        // then
        result.andExpect(status().isAccepted())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("주문을 거절했습니다. 결제 취소 결과를 확인하고 있습니다."))
            .andExpect(jsonPath("$.data.paymentStatus").value("CANCEL_REQUESTED"))
            .andExpect(jsonPath("$.data.refundStatus").value("REQUESTED"));
    }

    @Test
    @DisplayName("SUPER_ADMIN 토큰이면 마트 ID 없이 Facade를 부른다")
    void rejectBySuperAdmin() throws Exception {
        // given
        given(martRejectionFacade.reject(AdminRole.SUPER_ADMIN, null, ADMIN_ID, ORDER_ID, KEY, REASON))
            .willReturn(new MartRejectionPaymentResult(MartRejectionOutcome.PENDING,
                new MartRejectionResponse(ORDER_ID, 10L, PaymentStatus.CANCEL_REQUESTED, null, 77L,
                    RefundStatus.REQUESTED, 23800L)));

        // when
        ResultActions result = perform(superAdminToken(), KEY, REJECT_BODY);

        // then
        result.andExpect(status().isAccepted());
        verify(martRejectionFacade).reject(AdminRole.SUPER_ADMIN, null, ADMIN_ID, ORDER_ID, KEY, REASON);
    }

    @Test
    @DisplayName("Facade가 PAYMENT-009를 던지면 409 오류 봉투로 반환한다 — PortOne이 취소를 FAILED로 명시한 경우")
    void rejectWithPgFailure() throws Exception {
        // given
        given(martRejectionFacade.reject(AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID, KEY, REASON))
            .willThrow(new BusinessException(PaymentErrorCode.PAYMENT_CANCEL_REJECTED));

        // when
        ResultActions result = perform(martAdminToken(), KEY, REJECT_BODY);

        // then
        result.andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("PAYMENT-009"));
    }

    @Test
    @DisplayName("Idempotency-Key 헤더가 없으면 COMMON-007이고 Facade는 부르지 않는다")
    void rejectWithoutIdempotencyKey() throws Exception {
        // when
        ResultActions result = perform(martAdminToken(), null, REJECT_BODY);

        // then
        result.andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-007"));
        verifyFacadeNotCalled();
    }

    @Test
    @DisplayName("사유가 비어 있으면 COMMON-001과 필드 오류를 돌려주고 Facade는 부르지 않는다")
    void rejectWithBlankReason() throws Exception {
        // when
        ResultActions result = perform(martAdminToken(), KEY, "{\"reason\": \" \"}");

        // then
        result.andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-001"))
            .andExpect(jsonPath("$.errors[0].field").value("reason"));
        verifyFacadeNotCalled();
    }

    @Test
    @DisplayName("사유가 500자를 넘으면 COMMON-001과 필드 오류를 돌려주고 Facade는 부르지 않는다")
    void rejectWithTooLongReason() throws Exception {
        // when — ORDER.cancel_reason_detail이 500자라 입력 단계에서 막는다
        ResultActions result = perform(martAdminToken(), KEY, "{\"reason\": \"" + "가".repeat(501) + "\"}");

        // then
        result.andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-001"))
            .andExpect(jsonPath("$.errors[0].field").value("reason"));
        verifyFacadeNotCalled();
    }

    @Test
    @DisplayName("회원 토큰으로는 관리자 거절 API를 부를 수 없고 AUTH-004를 반환한다")
    void rejectWithMemberToken() throws Exception {
        // when
        ResultActions result = perform(tokenProvider.createMemberTokens(7L).accessToken(), KEY, REJECT_BODY);

        // then
        result.andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTH-004"));
        verifyFacadeNotCalled();
    }

    private ResultActions perform(String token, String idempotencyKey, String body) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/admin/orders/{orderId}/reject", ORDER_ID)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    private void verifyFacadeNotCalled() {
        verify(martRejectionFacade, never()).reject(any(), any(), any(), any(), any(), any());
    }

    private String martAdminToken() {
        return tokenProvider.createTokens(new AuthPrincipal(SubjectType.ADMIN, ADMIN_ID, "MART_ADMIN", MART_ID))
            .accessToken();
    }

    private String superAdminToken() {
        return tokenProvider.createTokens(new AuthPrincipal(SubjectType.ADMIN, ADMIN_ID, "SUPER_ADMIN", null))
            .accessToken();
    }
}
