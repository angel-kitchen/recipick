package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.RefundSummary;
import com.recipick.domain.payment.dto.response.PaymentDetailResponse;
import com.recipick.domain.payment.dto.response.PaymentHistoryResponse;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.RefundStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.PaymentService;
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
class PaymentQueryFacadeTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long ORDER_ID = 1L;
    private static final Long PAYMENT_ID = 10L;
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 22, 10, 5);

    @Mock
    private PaymentService paymentService;

    @Mock
    private OrderService orderService;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @InjectMocks
    private PaymentQueryFacade paymentQueryFacade;

    @Test
    @DisplayName("본인 주문이면 결제 정보를 돌려주고, 환불이 없으면 완료 금액 0·최근 상태 null이다")
    void getByOrderId() {
        // given
        given(paymentService.getByOrderId(ORDER_ID)).willReturn(inProgressPayment());
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(paymentService.getRefundSummary(PAYMENT_ID)).willReturn(RefundSummary.NONE);

        // when
        PaymentDetailResponse response = paymentQueryFacade.getByOrderId(MEMBER_ID, ORDER_ID);

        // then
        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.portonePaymentId()).isEqualTo("pay_1");
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(response.amount()).isEqualTo(23800L);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(response.failureReason()).isNull();
        assertThat(response.paidAt()).isNull();
        assertThat(response.canceledAt()).isNull();
        assertThat(response.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(response.completedRefundAmount()).isZero();
        assertThat(response.latestRefundStatus()).isNull();
    }

    @Test
    @DisplayName("실패한 결제는 실패 사유와 환불 요약을 함께 돌려준다")
    void getByOrderIdWithRefund() {
        // given
        given(paymentService.getByOrderId(ORDER_ID)).willReturn(failedPayment());
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);
        given(paymentService.getRefundSummary(PAYMENT_ID))
            .willReturn(new RefundSummary(20000L, RefundStatus.COMPLETED));

        // when
        PaymentDetailResponse response = paymentQueryFacade.getByOrderId(MEMBER_ID, ORDER_ID);

        // then
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo(PaymentFailureReason.AMOUNT_MISMATCH);
        assertThat(response.completedRefundAmount()).isEqualTo(20000L);
        assertThat(response.latestRefundStatus()).isEqualTo(RefundStatus.COMPLETED);
    }

    @Test
    @DisplayName("다른 회원의 주문이면 PAYMENT-001이고 환불 이력은 읽지 않는다")
    void getByOrderIdWithOtherMember() {
        // given
        given(paymentService.getByOrderId(ORDER_ID)).willReturn(inProgressPayment());
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID + 1);

        // when
        Throwable thrown = catchThrowable(() -> paymentQueryFacade.getByOrderId(MEMBER_ID, ORDER_ID));

        // then
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND));
        verify(paymentService, never()).getRefundSummary(anyLong());
    }

    @Test
    @DisplayName("결제가 없으면 PAYMENT-001이고 주문은 조회하지 않는다")
    void getByOrderIdWithoutPayment() {
        // given
        given(paymentService.getByOrderId(ORDER_ID))
            .willThrow(new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        // when
        Throwable thrown = catchThrowable(() -> paymentQueryFacade.getByOrderId(MEMBER_ID, ORDER_ID));

        // then
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND));
        verify(orderService, never()).getMemberIdOf(anyLong());
    }

    @Test
    @DisplayName("내 결제 내역은 주문 ID로 결제를 찾고 환불 요약을 붙여 돌려준다")
    void findMyHistory() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        given(orderService.findOrderIdsOf(MEMBER_ID)).willReturn(List.of(ORDER_ID));
        given(paymentService.findHistory(anyCollection(), any(Pageable.class)))
            .willReturn(new PageImpl<>(List.of(failedPayment()), pageable, 1));
        given(paymentService.getRefundSummaries(List.of(PAYMENT_ID)))
            .willReturn(Map.of(PAYMENT_ID, new RefundSummary(20000L, RefundStatus.COMPLETED)));

        // when
        PageResponse<PaymentHistoryResponse> response = paymentQueryFacade.findMyHistory(MEMBER_ID, pageable);

        // then
        assertThat(response.content()).hasSize(1);
        PaymentHistoryResponse first = response.content().get(0);
        assertThat(first.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(first.orderId()).isEqualTo(ORDER_ID);
        assertThat(first.failureReason()).isEqualTo(PaymentFailureReason.AMOUNT_MISMATCH);
        assertThat(first.completedRefundAmount()).isEqualTo(20000L);
        assertThat(first.latestRefundStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("환불 이력이 없는 결제는 완료 금액 0·최근 상태 null로 채운다")
    void findMyHistoryWithoutRefund() {
        // given — 요약 Map에 그 결제 키가 아예 없는 상황
        Pageable pageable = PageRequest.of(0, 20);
        given(orderService.findOrderIdsOf(MEMBER_ID)).willReturn(List.of(ORDER_ID));
        given(paymentService.findHistory(anyCollection(), any(Pageable.class)))
            .willReturn(new PageImpl<>(List.of(inProgressPayment()), pageable, 1));
        given(paymentService.getRefundSummaries(List.of(PAYMENT_ID))).willReturn(Map.of());

        // when
        PageResponse<PaymentHistoryResponse> response = paymentQueryFacade.findMyHistory(MEMBER_ID, pageable);

        // then
        assertThat(response.content().get(0).completedRefundAmount()).isZero();
        assertThat(response.content().get(0).latestRefundStatus()).isNull();
    }

    @Test
    @DisplayName("주문이 하나도 없으면 결제·환불을 조회하지 않고 빈 페이지를 돌려준다")
    void findMyHistoryWithoutOrders() {
        // given
        given(orderService.findOrderIdsOf(MEMBER_ID)).willReturn(List.of());

        // when
        PageResponse<PaymentHistoryResponse> response =
            paymentQueryFacade.findMyHistory(MEMBER_ID, PageRequest.of(0, 20));

        // then
        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        verify(paymentService, never()).findHistory(anyCollection(), any());
        verify(paymentService, never()).getRefundSummaries(anyCollection());
    }

    @Test
    @DisplayName("주문은 있지만 결제 페이지가 비어 있으면 빈 목록으로 환불 요약을 부른다")
    void findMyHistoryWithEmptyPaymentPage() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        given(orderService.findOrderIdsOf(MEMBER_ID)).willReturn(List.of(ORDER_ID));
        given(paymentService.findHistory(anyCollection(), any(Pageable.class)))
            .willReturn(Page.empty(pageable));
        given(paymentService.getRefundSummaries(List.of())).willReturn(Map.of());

        // when
        PageResponse<PaymentHistoryResponse> response = paymentQueryFacade.findMyHistory(MEMBER_ID, pageable);

        // then — 빈 목록이 넘어가므로 Service가 환불 Repository를 부르지 않는다(PaymentServiceTest에서 검증)
        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        verify(paymentService).getRefundSummaries(List.of());
    }

    @Test
    @DisplayName("클라이언트가 다른 정렬을 보내도 createdAt·id 내림차순으로 바꿔 조회한다")
    void findMyHistoryIgnoresClientSort() {
        // given — 금액 오름차순을 요청해도 서버 정렬로 덮어써야 한다
        Pageable clientPageable = PageRequest.of(1, 5, Sort.by(Sort.Direction.ASC, "amount"));
        given(orderService.findOrderIdsOf(MEMBER_ID)).willReturn(List.of(ORDER_ID));
        given(paymentService.findHistory(anyCollection(), any(Pageable.class)))
            .willReturn(Page.empty(clientPageable));
        given(paymentService.getRefundSummaries(List.of())).willReturn(Map.of());

        // when
        paymentQueryFacade.findMyHistory(MEMBER_ID, clientPageable);

        // then — page·size는 그대로, 정렬만 서버 것으로
        verify(paymentService).findHistory(anyCollection(), pageableCaptor.capture());
        Pageable used = pageableCaptor.getValue();
        assertThat(used.getPageNumber()).isEqualTo(1);
        assertThat(used.getPageSize()).isEqualTo(5);
        assertThat(used.getSort()).isEqualTo(
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private Payment inProgressPayment() {
        Payment payment = Payment.builder()
            .orderId(ORDER_ID)
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId("pay_1")
            .amount(23800L)
            .status(PaymentStatus.IN_PROGRESS)
            .expiresAt(EXPIRES_AT)
            .build();
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        return payment;
    }

    // 실제 전이 메서드로 실패시켜 failureReason이 채워진 결제를 만든다
    private Payment failedPayment() {
        Payment payment = inProgressPayment();
        payment.fail(PaymentFailureReason.AMOUNT_MISMATCH, "tx_1");
        return payment;
    }
}
