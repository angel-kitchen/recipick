package com.recipick.domain.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recipick.domain.payment.dto.response.PaymentCancelResponse;
import com.recipick.domain.payment.dto.response.PaymentConfirmResponse;
import com.recipick.domain.payment.dto.response.PaymentDetailResponse;
import com.recipick.domain.payment.dto.response.PaymentHistoryResponse;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.facade.PaymentCancelFacade;
import com.recipick.domain.payment.facade.PaymentConfirmFacade;
import com.recipick.domain.payment.facade.PaymentQueryFacade;
import com.recipick.global.config.ClockConfig;
import com.recipick.global.config.JacksonConfig;
import com.recipick.global.config.JwtConfig;
import com.recipick.global.config.SecurityConfig;
import com.recipick.global.config.WebConfig;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.GlobalExceptionHandler;
import com.recipick.global.response.PageResponse;
import com.recipick.global.security.JwtAccessDeniedHandler;
import com.recipick.global.security.JwtAuthenticationEntryPoint;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.LoginPrincipalArgumentResolver;
import com.recipick.global.security.SecurityErrorResponseWriter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
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
    private static final String CANCEL_BODY = """
            {"reason": "단순 변심"}
            """;
    private static final String IDEMPOTENCY_KEY = "cancel-key-1";

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @MockitoBean
    private PaymentConfirmFacade paymentConfirmFacade;

    @MockitoBean
    private PaymentCancelFacade paymentCancelFacade;

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
    @DisplayName("회원 토큰·Idempotency-Key·사유가 있으면 토큰의 회원 ID로 Facade를 부르고 결제 전 취소 응답을 돌려준다")
    void cancel() throws Exception {
        // given
        given(paymentCancelFacade.cancel(MEMBER_ID, 10L, IDEMPOTENCY_KEY, "단순 변심")).willReturn(
            new PaymentCancelResponse(10L, 1L, PaymentStatus.FAILED, PaymentFailureReason.CANCELED_BEFORE_PAY,
                null, null, 0L));

        // when
        ResultActions result = mockMvc.perform(post("/api/payments/{paymentId}/cancel", 10L)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken())
            .header("Idempotency-Key", IDEMPOTENCY_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content(CANCEL_BODY));

        // then — 결제 전 취소는 PG를 거치지 않아 canceledAt이 없고 REFUND도 없다
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.message").value("결제 대기 주문을 취소했습니다."))
            .andExpect(jsonPath("$.data.paymentId").value(10))
            .andExpect(jsonPath("$.data.orderId").value(1))
            .andExpect(jsonPath("$.data.paymentStatus").value("FAILED"))
            .andExpect(jsonPath("$.data.failureReason").value("CANCELED_BEFORE_PAY"))
            .andExpect(jsonPath("$.data.canceledAt").value(nullValue()))
            .andExpect(jsonPath("$.data.refundStatus").value(nullValue()))
            .andExpect(jsonPath("$.data.refundAmount").value(0));
        verify(paymentCancelFacade).cancel(MEMBER_ID, 10L, IDEMPOTENCY_KEY, "단순 변심");
    }

    @Test
    @DisplayName("토큰이 없으면 취소할 수 없고 AUTH-001을 반환한다")
    void cancelWithoutToken() throws Exception {
        // when
        ResultActions result = mockMvc.perform(post("/api/payments/{paymentId}/cancel", 10L)
            .header("Idempotency-Key", IDEMPOTENCY_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content(CANCEL_BODY));

        // then
        result.andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("AUTH-001"));
        verify(paymentCancelFacade, never()).cancel(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("Idempotency-Key 헤더가 없으면 GlobalExceptionHandler가 COMMON-007로 답하고 Facade는 부르지 않는다")
    void cancelWithoutIdempotencyKey() throws Exception {
        // when
        ResultActions result = mockMvc.perform(post("/api/payments/{paymentId}/cancel", 10L)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(CANCEL_BODY));

        // then
        result.andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COMMON-007"));
        verify(paymentCancelFacade, never()).cancel(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("사유가 비어 있으면 COMMON-001과 필드 오류를 돌려주고 Facade는 부르지 않는다")
    void cancelWithBlankReason() throws Exception {
        // when
        ResultActions result = mockMvc.perform(post("/api/payments/{paymentId}/cancel", 10L)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken())
            .header("Idempotency-Key", IDEMPOTENCY_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\": \" \"}"));

        // then
        result.andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COMMON-001"))
            .andExpect(jsonPath("$.errors[0].field").value("reason"));
        verify(paymentCancelFacade, never()).cancel(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("사유가 500자를 넘으면 COMMON-001과 필드 오류를 돌려주고 Facade는 부르지 않는다")
    void cancelWithTooLongReason() throws Exception {
        // when — ORDER.cancel_reason_detail이 500자라 입력 단계에서 막는다
        ResultActions result = mockMvc.perform(post("/api/payments/{paymentId}/cancel", 10L)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken())
            .header("Idempotency-Key", IDEMPOTENCY_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\": \"" + "가".repeat(501) + "\"}"));

        // then
        result.andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COMMON-001"))
            .andExpect(jsonPath("$.errors[0].field").value("reason"));
        verify(paymentCancelFacade, never()).cancel(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("Facade가 PAYMENT-007을 던지면 409 오류 봉투로 반환한다")
    void cancelWithInvalidStatus() throws Exception {
        // given
        given(paymentCancelFacade.cancel(anyLong(), anyLong(), anyString(), anyString()))
            .willThrow(new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS));

        // when
        ResultActions result = mockMvc.perform(post("/api/payments/{paymentId}/cancel", 10L)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken())
            .header("Idempotency-Key", IDEMPOTENCY_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content(CANCEL_BODY));

        // then
        result.andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("PAYMENT-007"))
            .andExpect(jsonPath("$.message").value("현재 결제 상태에서는 요청을 처리할 수 없습니다."));
    }

    @Test
    @DisplayName("회원 토큰으로 주문 결제를 조회하면 토큰의 회원 ID로 Facade를 부르고 결제 정보를 돌려준다")
    void getByOrderId() throws Exception {
        // given
        given(paymentQueryFacade.getByOrderId(MEMBER_ID, 1L)).willReturn(new PaymentDetailResponse(
            10L, 1L, "pay_1", PaymentMethod.CARD, 23800L, PaymentStatus.IN_PROGRESS, null,
            null, null, LocalDateTime.of(2026, 10, 22, 10, 5), 0L, null));

        // when
        ResultActions result = mockMvc.perform(get("/api/payments/orders/{orderId}", 1L)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken()));

        // then
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.message").value("결제 정보를 조회했습니다."))
            .andExpect(jsonPath("$.data.paymentId").value(10))
            .andExpect(jsonPath("$.data.orderId").value(1))
            .andExpect(jsonPath("$.data.portonePaymentId").value("pay_1"))
            .andExpect(jsonPath("$.data.paymentMethod").value("CARD"))
            .andExpect(jsonPath("$.data.amount").value(23800))
            .andExpect(jsonPath("$.data.paymentStatus").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.failureReason").value(nullValue()))
            .andExpect(jsonPath("$.data.paidAt").value(nullValue()))
            .andExpect(jsonPath("$.data.canceledAt").value(nullValue()))
            .andExpect(jsonPath("$.data.expiresAt").value("2026-10-22T10:05:00+09:00"))
            .andExpect(jsonPath("$.data.completedRefundAmount").value(0))
            .andExpect(jsonPath("$.data.latestRefundStatus").value(nullValue()));
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

    @Test
    @DisplayName("회원 토큰으로 결제 내역을 조회하면 기본 페이징(0·20)으로 Facade를 부르고 페이지 응답을 돌려준다")
    void findMyHistory() throws Exception {
        // given
        given(paymentQueryFacade.findMyHistory(anyLong(), any(Pageable.class)))
            .willReturn(new PageResponse<>(List.of(new PaymentHistoryResponse(
                10L, 1L, PaymentMethod.CARD, 23800L, PaymentStatus.CANCELED, null,
                LocalDateTime.of(2026, 10, 22, 10, 3), LocalDateTime.of(2026, 10, 22, 10, 8),
                LocalDateTime.of(2026, 10, 22, 10, 0), 23800L, RefundStatus.COMPLETED)),
                0, 20, 1, 1));

        // when
        ResultActions result = mockMvc.perform(get("/api/payments")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken()));

        // then
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.message").value("결제 내역을 조회했습니다."))
            .andExpect(jsonPath("$.data.content[0].paymentId").value(10))
            .andExpect(jsonPath("$.data.content[0].orderId").value(1))
            .andExpect(jsonPath("$.data.content[0].paymentMethod").value("CARD"))
            .andExpect(jsonPath("$.data.content[0].amount").value(23800))
            .andExpect(jsonPath("$.data.content[0].paymentStatus").value("CANCELED"))
            .andExpect(jsonPath("$.data.content[0].failureReason").value(nullValue()))
            .andExpect(jsonPath("$.data.content[0].paidAt").value("2026-10-22T10:03:00+09:00"))
            .andExpect(jsonPath("$.data.content[0].canceledAt").value("2026-10-22T10:08:00+09:00"))
            .andExpect(jsonPath("$.data.content[0].createdAt").value("2026-10-22T10:00:00+09:00"))
            .andExpect(jsonPath("$.data.content[0].completedRefundAmount").value(23800))
            .andExpect(jsonPath("$.data.content[0].latestRefundStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.totalPages").value(1))
            // 목록 응답에는 상세 전용 필드가 없다
            .andExpect(jsonPath("$.data.content[0].portonePaymentId").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].expiresAt").doesNotExist());

        // 파라미터를 안 보내면 application.yml의 기본값(page 0, size 20)이 들어온다
        verify(paymentQueryFacade).findMyHistory(eq(MEMBER_ID), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("size를 100보다 크게 보내도 전역 설정대로 100으로 잘려 Facade에 전달된다")
    void findMyHistoryWithOversizedPage() throws Exception {
        // given
        given(paymentQueryFacade.findMyHistory(anyLong(), any(Pageable.class)))
            .willReturn(new PageResponse<>(List.of(), 0, 100, 0, 0));

        // when
        mockMvc.perform(get("/api/payments")
                .param("size", "500")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken()))
            .andExpect(status().isOk());

        // then — application.yml의 spring.data.web.pageable.max-page-size
        verify(paymentQueryFacade).findMyHistory(eq(MEMBER_ID), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("토큰이 없으면 결제 내역을 조회할 수 없고 AUTH-001을 반환한다")
    void findMyHistoryWithoutToken() throws Exception {
        // when
        ResultActions result = mockMvc.perform(get("/api/payments"));

        // then
        result.andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("AUTH-001"));
        verify(paymentQueryFacade, never()).findMyHistory(anyLong(), any());
    }

    private String memberToken() {
        return tokenProvider.createMemberTokens(MEMBER_ID).accessToken();
    }
}
