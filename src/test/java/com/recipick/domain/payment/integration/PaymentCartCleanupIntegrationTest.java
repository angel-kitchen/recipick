package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.repository.CartItemRepository;
import com.recipick.domain.order.service.CartService;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.facade.PaymentConfirmProcessor;
import com.recipick.domain.payment.facade.PaymentExpirationProcessor;
import com.recipick.domain.payment.service.PaymentApprovalOutcome;
import com.recipick.domain.payment.service.PaymentExpirationOutcome;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// 카드 결제 결과에 따라 결제한 상품·수량만 장바구니에서 빠지는지 실제 MySQL 위에서 본다.
// 결제에 실패하거나 만료된 주문은 회원이 다시 담지 않아도 되게 장바구니를 그대로 둔다.
// 정리는 결제 확정이 커밋된 뒤 따로 하므로, 정리가 실패해도 결제 확정은 그대로 남아야 한다
class PaymentCartCleanupIntegrationTest extends PaymentFlowIntegrationSupport {

    // 주문에 없는 상품. cart_item은 상품을 외래키로 잡지 않아 실제 상품 행 없이 ID만 쓴다
    private static final long OTHER_PRODUCT_OFFSET = 9_400_000_000L;

    @Autowired
    CartItemRepository cartItemRepository;

    @MockitoSpyBean
    CartService cartService;

    @Autowired
    PaymentConfirmProcessor paymentConfirmProcessor;

    @Autowired
    PaymentExpirationProcessor paymentExpirationProcessor;

    private final List<Long> memberIds = new ArrayList<>();

    @AfterEach
    void cleanUpCart() {
        memberIds.forEach(memberId -> cartItemRepository.deleteAll(cartItemRepository.findAllByMemberIdOrderByIdAsc(memberId)));
        memberIds.clear();
    }

    @Test
    @DisplayName("결제가 승인되면 주문한 상품만 장바구니에서 지우고 주문에 없는 상품은 남긴다")
    void approvalRemovesPaidItems() {
        // given
        Flow flow = pendingPaymentWithStock(LocalDateTime.now(clock).withNano(0).plusMinutes(5));
        CartItem paid = cartItem(flow, flow.productId());
        CartItem other = cartItem(flow, OTHER_PRODUCT_OFFSET + flow.orderId());

        // when
        PaymentApprovalOutcome outcome = paymentConfirmProcessor.process(flow.orderId(), paidResult(flow));

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.APPROVED);
        assertThat(orderRepository.findById(flow.orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(cartItemRepository.findById(paid.getId())).isEmpty();
        assertThat(cartItemRepository.findById(other.getId())).isPresent();
    }

    @Test
    @DisplayName("결제 대기 중 같은 상품을 더 담았으면 결제한 수량만 빼고 나머지는 남기며, 줄였으면 항목을 지운다")
    void approvalDeductsPaidQuantity() {
        // given — 두 주문 모두 2개를 주문했다. 하나는 결제 전 5개로 늘렸고, 하나는 1개로 줄였다
        Flow increased = pendingPaymentWithStock(LocalDateTime.now(clock).withNano(0).plusMinutes(5));
        CartItem more = cartItem(increased, increased.productId(), ORDER_QUANTITY + 3);
        Flow decreased = pendingPaymentWithStock(LocalDateTime.now(clock).withNano(0).plusMinutes(5));
        CartItem less = cartItem(decreased, decreased.productId(), ORDER_QUANTITY - 1);

        // when
        paymentConfirmProcessor.process(increased.orderId(), paidResult(increased));
        paymentConfirmProcessor.process(decreased.orderId(), paidResult(decreased));

        // then
        CartItem left = cartItemRepository.findById(more.getId()).orElseThrow();
        assertThat(left.getQuantity()).isEqualTo(3);
        assertThat(cartItemRepository.findById(less.getId())).isEmpty();
    }

    @Test
    @DisplayName("장바구니 정리가 잠금 대기 초과로 실패해도 결제 확정은 커밋된 채 승인으로 끝나고 장바구니만 남는다")
    void cleanupFailureKeepsPayment() {
        // given
        Flow flow = pendingPaymentWithStock(LocalDateTime.now(clock).withNano(0).plusMinutes(5));
        CartItem cartItem = cartItem(flow, flow.productId());
        doThrow(new CannotAcquireLockException("lock wait timeout"))
            .when(cartService).deductPaidItems(eq(flow.memberId()), any());

        // when
        PaymentApprovalOutcome outcome = paymentConfirmProcessor.process(flow.orderId(), paidResult(flow));

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.APPROVED);
        assertThat(orderRepository.findById(flow.orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentRepository.findById(flow.paymentId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.PAID);
        assertThat(cartItemRepository.findById(cartItem.getId())).isPresent();
    }

    @Test
    @DisplayName("PG가 결제 실패를 확인하면 주문만 닫고 장바구니는 그대로 둔다")
    void pgFailureKeepsCart() {
        // given
        Flow flow = pendingPaymentWithStock(LocalDateTime.now(clock).withNano(0).plusMinutes(5));
        CartItem cartItem = cartItem(flow, flow.productId());
        PortOnePayment failed = new PortOnePayment(flow.portonePaymentId(), "tx_flow_" + flow.orderId(),
            PortOnePaymentStatus.FAILED, null, "KRW", null, flow.orderId(), "card declined");

        // when
        PaymentApprovalOutcome outcome = paymentConfirmProcessor.process(flow.orderId(), failed);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.PG_FAILED);
        assertThat(orderRepository.findById(flow.orderId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.CANCELED);
        assertThat(cartItemRepository.findById(cartItem.getId())).isPresent();
    }

    @Test
    @DisplayName("결제 대기 5분이 지나 만료되면 주문만 닫고 장바구니는 그대로 둔다")
    void expirationKeepsCart() {
        // given
        Flow flow = pendingPaymentWithStock(LocalDateTime.now(clock).withNano(0).minusMinutes(1));
        CartItem cartItem = cartItem(flow, flow.productId());

        // when
        PaymentExpirationOutcome outcome = paymentExpirationProcessor.expire(flow.paymentId());

        // then
        assertThat(outcome).isEqualTo(PaymentExpirationOutcome.EXPIRED);
        assertThat(orderRepository.findById(flow.orderId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.CANCELED);
        assertThat(cartItemRepository.findById(cartItem.getId())).isPresent();
    }

    // 재고 픽스처는 결제 시각을 두지 않아 승인 응답의 완료 시각을 직접 넣는다
    private PortOnePayment paidResult(Flow flow) {
        return new PortOnePayment(flow.portonePaymentId(), "tx_flow_" + flow.orderId(), PortOnePaymentStatus.PAID,
            AMOUNT, "KRW", LocalDateTime.now(clock).withNano(0).minusSeconds(10), flow.orderId(), null);
    }

    private CartItem cartItem(Flow flow, Long productId) {
        return cartItem(flow, productId, ORDER_QUANTITY);
    }

    private CartItem cartItem(Flow flow, Long productId, int quantity) {
        if (!memberIds.contains(flow.memberId())) {
            memberIds.add(flow.memberId());
        }
        return cartItemRepository.save(CartItem.builder()
            .memberId(flow.memberId())
            .productId(productId)
            .quantity(quantity)
            .build());
    }
}
