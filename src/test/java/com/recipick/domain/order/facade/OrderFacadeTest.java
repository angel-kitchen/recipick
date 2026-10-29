package com.recipick.domain.order.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.RegionService;
import com.recipick.domain.order.dto.OrderCreateResult;
import com.recipick.domain.order.dto.request.OrderCreateRequest;
import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.service.CartService;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.PaymentCreated;
import com.recipick.domain.payment.service.PaymentCommandService;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.payment.service.PointCommandService;
import com.recipick.domain.payment.service.PointPolicyService;
import com.recipick.domain.product.dto.MartResponse;
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.dto.StockQuantity;
import com.recipick.domain.product.entity.MartStatus;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.domain.product.service.MartService;
import com.recipick.domain.product.service.ProductService;
import com.recipick.domain.product.service.ProductStockService;
import com.recipick.global.common.SaleUnit;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long MART_ID = 2L;
    private static final Long REGION_ID = 3L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long CART_ITEM_ID = 20L;
    private static final Long ORDER_ID = 100L;
    private static final String LEGAL_DONG_CODE = "1111014000";
    private static final String IDEMPOTENCY_KEY = "key-1";
    private static final BigDecimal POINT_RATE = new BigDecimal("0.0050");

    @Mock
    private OrderService orderService;

    @Mock
    private CartService cartService;

    @Mock
    private MemberService memberService;

    @Mock
    private RegionService regionService;

    @Mock
    private MartService martService;

    @Mock
    private ProductService productService;

    @Mock
    private ProductStockService productStockService;

    @Mock
    private PaymentCommandService paymentCommandService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PointPolicyService pointPolicyService;

    @Mock
    private PointCommandService pointCommandService;

    @InjectMocks
    private OrderFacade orderFacade;

    @Test
    @DisplayName("새 주문은 적립률 결정 → 주문 저장 → 포인트 예약 → 재고 차감 → 금액 확정 → 적립 예정 → 결제 생성 순으로 처리해 포인트를 재고보다 먼저 잠근다")
    void createOrder_reservesPointBeforeStock() {
        // given — 2,500원 상품 2개(상품 5,000원 + 배송비 3,000원)에 3,000P를 쓰면 결제금액은 5,000원이다
        OrderCreateRequest request = new OrderCreateRequest(List.of(CART_ITEM_ID), "홍길동", "01012345678",
            "서울 종로구 삼청로 1", "101호", LEGAL_DONG_CODE, 3_000L);
        String requestHash = request.requestHash();
        List<StockQuantity> quantities = List.of(new StockQuantity(PRODUCT_ID, 2));
        List<ProductSummary> products = List.of(new ProductSummary(PRODUCT_ID, MART_ID, "애호박", null, 2_500L,
            BigDecimal.ONE, SaleUnit.EA, ProductStatus.SELLING, 8, false));
        Order order = pendingOrder();
        List<OrderItem> items = List.of(OrderItem.builder().productId(PRODUCT_ID).productName("애호박").quantity(2)
            .build());
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.of(MART_ID));
        given(martService.findMart(MART_ID)).willReturn(Optional.of(
            new MartResponse(MART_ID, REGION_ID, "삼청 마트", null, null, null, MartStatus.OPEN)));
        given(regionService.getRegionIdByLegalDongCode(LEGAL_DONG_CODE)).willReturn(Optional.of(REGION_ID));
        given(cartService.findCartItems(MEMBER_ID, List.of(CART_ITEM_ID))).willReturn(List.of(
            CartItem.builder().memberId(MEMBER_ID).productId(PRODUCT_ID).quantity(2).build()));
        given(productService.findMartIdsOf(List.of(PRODUCT_ID))).willReturn(Map.of(PRODUCT_ID, MART_ID));
        given(pointPolicyService.resolveRate(MEMBER_ID)).willReturn(POINT_RATE);
        given(orderService.createPendingOrder(
            MEMBER_ID, MART_ID, REGION_ID, IDEMPOTENCY_KEY, requestHash, POINT_RATE, request)).willReturn(order);
        given(productStockService.decreaseStocks(MART_ID, quantities)).willReturn(products);
        given(orderService.completePendingOrder(order, products, quantities)).willReturn(items);
        given(paymentCommandService.create(ORDER_ID, 5_000L)).willReturn(
            new PaymentCreated(30L, "pay_test", 5_000L, LocalDateTime.of(2026, 10, 29, 12, 5)));

        // when
        OrderCreateResult result = orderFacade.createOrder(MEMBER_ID, IDEMPOTENCY_KEY, request);

        // then
        assertThat(result.created()).isTrue();
        assertThat(result.response().usedPoint()).isEqualTo(3_000L);
        assertThat(result.response().amount()).isEqualTo(5_000L);
        InOrder inOrder = inOrder(
            pointPolicyService, orderService, pointCommandService, productStockService, paymentCommandService);
        inOrder.verify(pointPolicyService).resolveRate(MEMBER_ID);
        inOrder.verify(orderService).createPendingOrder(
            MEMBER_ID, MART_ID, REGION_ID, IDEMPOTENCY_KEY, requestHash, POINT_RATE, request);
        inOrder.verify(pointCommandService).reserveUsage(MEMBER_ID, ORDER_ID, 3_000L);
        inOrder.verify(productStockService).decreaseStocks(MART_ID, quantities);
        inOrder.verify(orderService).completePendingOrder(order, products, quantities);
        inOrder.verify(pointCommandService).createPendingEarning(MEMBER_ID, ORDER_ID, 5_000L, 3_000L, POINT_RATE);
        inOrder.verify(paymentCommandService).create(ORDER_ID, 5_000L);
    }

    @Test
    @DisplayName("결제 실패로 주문을 종료하면 재고는 건드리지 않고 복구할 상품 수량을 돌려준다")
    void closeByPaymentFailure() {
        // given
        given(orderService.closeByPaymentFailure(1L, CancelReasonCode.PAYMENT_FAILED, null)).willReturn(List.of(
            OrderItem.builder().productId(10L).quantity(2).build(),
            OrderItem.builder().productId(20L).quantity(1).build()));

        // when
        List<StockQuantity> result = orderFacade.closeByPaymentFailure(1L, CancelReasonCode.PAYMENT_FAILED, null);

        // then
        assertThat(result).containsExactly(new StockQuantity(10L, 2), new StockQuantity(20L, 1));
        verify(productStockService, never()).increaseStocks(any());
    }

    @Test
    @DisplayName("환불 완료로 바꾸면 재고는 건드리지 않고 복구할 상품 수량을 돌려준다")
    void markRefunded() {
        // given
        given(orderService.markRefunded(1L)).willReturn(List.of(
            OrderItem.builder().productId(10L).quantity(2).build()));

        // when
        List<StockQuantity> result = orderFacade.markRefunded(1L);

        // then
        assertThat(result).containsExactly(new StockQuantity(10L, 2));
        verify(productStockService, never()).increaseStocks(any());
    }

    @Test
    @DisplayName("외부 취소로 주문을 종료하면 재고는 건드리지 않고 복구할 상품 수량을 돌려준다")
    void closeByExternalCancel() {
        // given
        given(orderService.closeByExternalCancel(1L, "PortOne 콘솔 전액 취소")).willReturn(List.of(
            OrderItem.builder().productId(10L).quantity(2).build()));

        // when
        List<StockQuantity> result = orderFacade.closeByExternalCancel(1L, "PortOne 콘솔 전액 취소");

        // then
        assertThat(result).containsExactly(new StockQuantity(10L, 2));
        verify(productStockService, never()).increaseStocks(any());
    }

    @Test
    @DisplayName("복구할 상품 수량만큼 재고를 복구한다")
    void restoreStock() {
        // given
        List<StockQuantity> items = List.of(new StockQuantity(10L, 2), new StockQuantity(20L, 1));

        // when
        orderFacade.restoreStock(items);

        // then
        verify(productStockService).increaseStocks(items);
    }

    @Test
    @DisplayName("이미 종료된 주문이라 복구할 상품이 없으면 재고를 복구하지 않는다")
    void restoreStock_empty() {
        // when
        orderFacade.restoreStock(List.of());

        // then
        verify(productStockService, never()).increaseStocks(any());
    }

    // 금액 확정은 OrderService가 하므로, 확정을 마친 상태의 주문을 돌려줘 Facade가 그 값을 적립·결제에 넘기는지만 본다
    private Order pendingOrder() {
        Order order = Order.builder()
            .memberId(MEMBER_ID)
            .martId(MART_ID)
            .productAmount(5_000L)
            .deliveryFee(3_000L)
            .usedPoint(3_000L)
            .payAmount(5_000L)
            .pointRate(POINT_RATE)
            .build();
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        return order;
    }
}
