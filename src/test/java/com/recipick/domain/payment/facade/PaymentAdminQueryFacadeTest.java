package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.request.PaymentAdminSearchRequest;
import com.recipick.domain.payment.dto.response.PaymentAdminDetailResponse;
import com.recipick.domain.payment.dto.response.PaymentAdminSummaryResponse;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundRequester;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.repository.dto.PaymentAdminSearchCondition;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditRecorder;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentAdminQueryFacadeTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long ORDER_ID = 1L;
    private static final Long PAYMENT_ID = 10L;
    private static final Long MART_ID = 3L;
    private static final LocalDateTime REQUESTED_AT =
        LocalDateTime.of(2026, 10, 22, 10, 10);

    @Mock
    private PaymentService paymentService;

    @Mock
    private OrderService orderService;

    @Mock
    private AdminAuditRecorder adminAuditRecorder;

    @Captor
    private ArgumentCaptor<AdminAuditCommand> commandCaptor;

    @Captor
    private ArgumentCaptor<PaymentAdminSearchCondition> conditionCaptor;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @InjectMocks
    private PaymentAdminQueryFacade paymentAdminQueryFacade;

    @Test
    @DisplayName("회원 조건은 주문 ID로 바꿔 검색하고, 회원 ID·환불 요약을 붙인 뒤 PAYMENT_LIST_VIEW를 한 번 기록한다")
    void findPayments() {
        // given
        PaymentAdminSearchRequest request = searchRequest(MEMBER_ID);
        given(orderService.findOrderIdsOf(MEMBER_ID)).willReturn(List.of(ORDER_ID));
        given(paymentService.searchForAdmin(any(), any()))
            .willReturn(new PageImpl<>(List.of(payment()), PageRequest.of(0, 20), 1));
        given(paymentService.getRefundSummaries(List.of(PAYMENT_ID))).willReturn(Map.of());
        given(orderService.findMemberIdsOf(List.of(ORDER_ID)))
            .willReturn(Map.of(ORDER_ID, MEMBER_ID));

        // when
        PageResponse<PaymentAdminSummaryResponse> response =
            paymentAdminQueryFacade.findPayments(request);

        // then
        assertThat(response.totalElements()).isEqualTo(1);

        PaymentAdminSummaryResponse row = response.content().get(0);
        assertThat(row.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(row.memberId()).isEqualTo(MEMBER_ID);
        assertThat(row.refundedAmount()).isZero();
        assertThat(row.latestRefundStatus()).isNull();

        verify(paymentService)
            .searchForAdmin(conditionCaptor.capture(), pageableCaptor.capture());

        assertThat(conditionCaptor.getValue().orderIds())
            .containsExactly(ORDER_ID);

        assertThat(pageableCaptor.getValue().getSort())
            .isEqualTo(
                Sort.by(
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
                )
            );

        verify(adminAuditRecorder).record(commandCaptor.capture());

        assertThat(commandCaptor.getValue().action())
            .isEqualTo(AdminAuditAction.PAYMENT_LIST_VIEW);
        assertThat(commandCaptor.getValue().targetId()).isNull();
    }

    @Test
    @DisplayName("회원의 주문이 없으면 결제를 검색하지 않고 빈 페이지를 주지만, 조회 자체는 감사 기록한다")
    void findPaymentsOfMemberWithoutOrders() {
        // given
        given(orderService.findOrderIdsOf(MEMBER_ID))
            .willReturn(List.of());

        // when
        PageResponse<PaymentAdminSummaryResponse> response =
            paymentAdminQueryFacade.findPayments(searchRequest(MEMBER_ID));

        // then
        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();

        verify(paymentService, never())
            .searchForAdmin(any(), any());

        verify(adminAuditRecorder)
            .record(commandCaptor.capture());

        assertThat(commandCaptor.getValue().action())
            .isEqualTo(AdminAuditAction.PAYMENT_LIST_VIEW);
    }

    @Test
    @DisplayName("감사 기록이 실패하면 목록 응답을 돌려주지 않고 예외가 그대로 나간다")
    void findPaymentsFailsWhenAuditFails() {
        // given
        given(paymentService.searchForAdmin(any(), any()))
            .willReturn(Page.empty());

        willThrow(new IllegalStateException("감사 기록 실패"))
            .given(adminAuditRecorder)
            .record(any());

        // when
        Throwable thrown =
            catchThrowable(
                () -> paymentAdminQueryFacade.findPayments(searchRequest(null))
            );

        // then
        assertThat(thrown)
            .isInstanceOf(IllegalStateException.class);

        verify(orderService, never())
            .findOrderIdsOf(any());
    }

    @Test
    @DisplayName("결제의 주문 회원 정보를 찾을 수 없으면 데이터 정합성 오류로 실패한다")
    void findPaymentsFailsWhenOrderMemberIsMissing() {
        // given
        PaymentAdminSearchRequest request = searchRequest(null);

        given(paymentService.searchForAdmin(any(), any()))
            .willReturn(
                new PageImpl<>(
                    List.of(payment()),
                    PageRequest.of(0, 20),
                    1
                )
            );

        given(paymentService.getRefundSummaries(List.of(PAYMENT_ID)))
            .willReturn(Map.of());

        given(orderService.findMemberIdsOf(List.of(ORDER_ID)))
            .willReturn(Map.of());

        // when
        Throwable thrown =
            catchThrowable(
                () -> paymentAdminQueryFacade.findPayments(request)
            );

        // then
        assertThat(thrown)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("orderId=" + ORDER_ID);

        verify(adminAuditRecorder, never())
            .record(any());
    }

    @Test
    @DisplayName("상세는 회원 ID와 환불 시도 전체를 받은 순서(최신부터) 그대로 담고 PAYMENT_DETAIL_VIEW를 결제 ID로 기록한다")
    void getPayment() {
        // given
        Refund completed = refund(21L, RefundStatus.COMPLETED);
        Refund failed = refund(20L, RefundStatus.FAILED);

        given(paymentService.getById(PAYMENT_ID))
            .willReturn(payment());

        given(orderService.getMemberIdOf(ORDER_ID))
            .willReturn(MEMBER_ID);

        given(orderService.getMartIdOf(ORDER_ID))
            .willReturn(MART_ID);

        given(paymentService.findRefundsOf(PAYMENT_ID))
            .willReturn(List.of(completed, failed));

        // when
        PaymentAdminDetailResponse response =
            paymentAdminQueryFacade.getPayment(PAYMENT_ID);

        // then
        assertThat(response.paymentId())
            .isEqualTo(PAYMENT_ID);

        assertThat(response.memberId())
            .isEqualTo(MEMBER_ID);

        assertThat(response.portonePaymentId())
            .isEqualTo("pay_1");

        assertThat(response.refunds())
            .extracting(PaymentAdminDetailResponse.RefundHistory::refundId)
            .containsExactly(21L, 20L);

        assertThat(response.refunds())
            .extracting(PaymentAdminDetailResponse.RefundHistory::status)
            .containsExactly(
                RefundStatus.COMPLETED,
                RefundStatus.FAILED
            );

        verify(adminAuditRecorder)
            .record(commandCaptor.capture());

        assertThat(commandCaptor.getValue().action())
            .isEqualTo(AdminAuditAction.PAYMENT_DETAIL_VIEW);

        assertThat(commandCaptor.getValue().targetId())
            .isEqualTo(PAYMENT_ID);

        assertThat(commandCaptor.getValue().martId())
            .isEqualTo(MART_ID);
    }

    @Test
    @DisplayName("결제가 없으면 PAYMENT-001이고 회원 조회·감사 기록을 하지 않는다")
    void getPaymentNotFound() {
        // given
        given(paymentService.getById(PAYMENT_ID))
            .willThrow(
                new BusinessException(
                    PaymentErrorCode.PAYMENT_NOT_FOUND
                )
            );

        // when
        Throwable thrown =
            catchThrowable(
                () -> paymentAdminQueryFacade.getPayment(PAYMENT_ID)
            );

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class);

        assertThat(((BusinessException) thrown).getErrorCode())
            .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);

        verifyNoInteractions(
            orderService,
            adminAuditRecorder
        );
    }

    private PaymentAdminSearchRequest searchRequest(Long memberId) {
        return new PaymentAdminSearchRequest(
            null,
            memberId,
            null,
            null,
            null,
            null,
            null
        );
    }

    private Payment payment() {
        Payment payment = Payment.builder()
            .orderId(ORDER_ID)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(PaymentStatus.IN_PROGRESS)
            .expiresAt(REQUESTED_AT)
            .build();

        ReflectionTestUtils.setField(
            payment,
            "id",
            PAYMENT_ID
        );

        return payment;
    }

    private Refund refund(Long refundId, RefundStatus status) {
        Refund refund = Refund.builder()
            .payment(payment())
            .amount(23800L)
            .reason("단순 변심")
            .status(status)
            .requestedBy(RefundRequester.MEMBER)
            .idempotencyKey("key-" + refundId)
            .requestedAt(REQUESTED_AT)
            .build();

        ReflectionTestUtils.setField(
            refund,
            "id",
            refundId
        );

        return refund;
    }
}
