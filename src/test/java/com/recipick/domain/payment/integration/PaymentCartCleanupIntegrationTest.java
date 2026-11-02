package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.repository.CartItemRepository;
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

// 카드 결제 결과에 따라 결제한 상품만 장바구니에서 빠지는지 실제 MySQL 위에서 본다.
// 결제에 실패하거나 만료된 주문은 회원이 다시 담지 않아도 되게 장바구니를 그대로 둔다
class PaymentCartCleanupIntegrationTest extends PaymentFlowIntegrationSupport {

    // 주문에 없는 상품. cart_item은 상품을 외래키로 잡지 않아 실제 상품 행 없이 ID만 쓴다
    private static final long OTHER_PRODUCT_OFFSET = 9_400_000_000L;

    @Autowired
    CartItemRepository cartItemRepository;

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

        // 재고 픽스처는 결제 시각을 두지 않아 승인 응답의 완료 시각을 직접 넣는다
        PortOnePayment paidResult = new PortOnePayment(flow.portonePaymentId(), "tx_flow_" + flow.orderId(),
            PortOnePaymentStatus.PAID, AMOUNT, "KRW", LocalDateTime.now(clock).withNano(0).minusSeconds(10),
            flow.orderId(), null);

        // when
        PaymentApprovalOutcome outcome = paymentConfirmProcessor.process(flow.orderId(), paidResult);

        // then
        assertThat(outcome).isEqualTo(PaymentApprovalOutcome.APPROVED);
        assertThat(orderRepository.findById(flow.orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(cartItemRepository.findById(paid.getId())).isEmpty();
        assertThat(cartItemRepository.findById(other.getId())).isPresent();
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

    private CartItem cartItem(Flow flow, Long productId) {
        if (!memberIds.contains(flow.memberId())) {
            memberIds.add(flow.memberId());
        }
        return cartItemRepository.save(CartItem.builder()
            .memberId(flow.memberId())
            .productId(productId)
            .quantity(ORDER_QUANTITY)
            .build());
    }
}
