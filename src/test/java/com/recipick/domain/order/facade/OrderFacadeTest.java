package com.recipick.domain.order.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipService;
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
import com.recipick.domain.payment.facade.PointsOnlyPaymentProcessor;
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
    private MembershipService membershipService;

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

    @Mock
    private PointsOnlyPaymentProcessor pointsOnlyPaymentProcessor;

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
        List<ProductSummary> products = List.of(product(2_500L));
        Order order = pendingOrder(5_000L, 3_000L, 3_000L, 5_000L);
        givenOrderCreation(request, quantities, products, order, false);
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
        inOrder.verify(orderService).completePendingOrder(order, products, quantities, false);
        inOrder.verify(pointCommandService).createPendingEarning(MEMBER_ID, ORDER_ID, 5_000L, 3_000L, POINT_RATE);
        inOrder.verify(paymentCommandService).create(ORDER_ID, 5_000L);
        verify(pointsOnlyPaymentProcessor, never()).complete(any());
        // 카드 결제는 아직 승인 전이라 장바구니를 지우지 않는다
        verify(cartService, never()).deleteByProductIds(any(), any());
    }

    @Test
    @DisplayName("외부 결제금액이 0원이면 카드 결제를 만들지 않고 적립 예정 생성 뒤 0원 결제 완료 처리를 부르고 마지막에 결제한 상품을 장바구니에서 지운다")
    void createOrder_pointsOnly() {
        // given — 20,000원 상품 2개(상품 40,000원, 무료배송)를 40,000P로 사면 외부 결제금액은 0원이다
        OrderCreateRequest request = new OrderCreateRequest(List.of(CART_ITEM_ID), "홍길동", "01012345678",
            "서울 종로구 삼청로 1", "101호", LEGAL_DONG_CODE, 40_000L);
        List<StockQuantity> quantities = List.of(new StockQuantity(PRODUCT_ID, 2));
        List<ProductSummary> products = List.of(product(20_000L));
        Order order = pendingOrder(40_000L, 0L, 40_000L, 0L);
        givenOrderCreation(request, quantities, products, order, false);
        given(pointsOnlyPaymentProcessor.complete(ORDER_ID)).willReturn(
            new PaymentCreated(30L, null, 0L, null, LocalDateTime.of(2026, 10, 29, 12, 0)));
        given(orderService.findOrderItems(ORDER_ID)).willReturn(List.of(
            OrderItem.builder().productId(PRODUCT_ID).build()));
        given(orderService.getMemberIdOf(ORDER_ID)).willReturn(MEMBER_ID);

        // when
        OrderCreateResult result = orderFacade.createOrder(MEMBER_ID, IDEMPOTENCY_KEY, request);

        // then
        assertThat(result.created()).isTrue();
        assertThat(result.response().amount()).isZero();
        assertThat(result.response().portonePaymentId()).isNull();
        assertThat(result.response().expiresAt()).isNull();
        InOrder inOrder = inOrder(pointCommandService, productStockService, pointsOnlyPaymentProcessor, cartService);
        inOrder.verify(pointCommandService).reserveUsage(MEMBER_ID, ORDER_ID, 40_000L);
        inOrder.verify(productStockService).decreaseStocks(MART_ID, quantities);
        inOrder.verify(pointCommandService).createPendingEarning(MEMBER_ID, ORDER_ID, 40_000L, 40_000L, POINT_RATE);
        inOrder.verify(pointsOnlyPaymentProcessor).complete(ORDER_ID);
        inOrder.verify(cartService).deleteByProductIds(MEMBER_ID, List.of(PRODUCT_ID));
        verify(paymentCommandService, never()).create(anyLong(), anyLong());
    }

    @Test
    @DisplayName("멤버십 회원이면 금액 확정에 멤버십 배송비 기준을 쓰도록 활성 여부를 넘긴다")
    void createOrder_membershipDeliveryFee() {
        // given — 7,500원 상품 2개(상품 15,000원)는 멤버십 회원이면 무료배송이다
        OrderCreateRequest request = new OrderCreateRequest(List.of(CART_ITEM_ID), "홍길동", "01012345678",
            "서울 종로구 삼청로 1", "101호", LEGAL_DONG_CODE, 0L);
        List<StockQuantity> quantities = List.of(new StockQuantity(PRODUCT_ID, 2));
        List<ProductSummary> products = List.of(product(7_500L));
        Order order = pendingOrder(15_000L, 0L, 0L, 15_000L);
        givenOrderCreation(request, quantities, products, order, true);
        given(paymentCommandService.create(ORDER_ID, 15_000L)).willReturn(
            new PaymentCreated(30L, "pay_test", 15_000L, LocalDateTime.of(2026, 10, 29, 12, 5)));

        // when
        OrderCreateResult result = orderFacade.createOrder(MEMBER_ID, IDEMPOTENCY_KEY, request);

        // then
        assertThat(result.response().amount()).isEqualTo(15_000L);
        verify(orderService).completePendingOrder(order, products, quantities, true);
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

    // 새 주문 생성이 결제 생성 직전까지 거치는 회원·마트·지역·장바구니·적립률·주문·재고·금액 확정 호출을 준비한다
    private void givenOrderCreation(OrderCreateRequest request, List<StockQuantity> quantities,
                                    List<ProductSummary> products, Order order, boolean membershipActive) {
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
        given(orderService.createPendingOrder(MEMBER_ID, MART_ID, REGION_ID, IDEMPOTENCY_KEY, request.requestHash(),
            POINT_RATE, request)).willReturn(order);
        given(productStockService.decreaseStocks(MART_ID, quantities)).willReturn(products);
        given(membershipService.isActive(MEMBER_ID)).willReturn(membershipActive);
        given(orderService.completePendingOrder(order, products, quantities, membershipActive)).willReturn(items);
    }

    private static ProductSummary product(long price) {
        return new ProductSummary(PRODUCT_ID, MART_ID, "애호박", null, price, BigDecimal.ONE, SaleUnit.EA,
            ProductStatus.SELLING, 8, false);
    }

    // 금액 확정은 OrderService가 하므로, 확정을 마친 상태의 주문을 돌려줘 Facade가 그 값을 적립·결제에 넘기는지만 본다
    private Order pendingOrder(long productAmount, long deliveryFee, long usedPoint, long payAmount) {
        Order order = Order.builder()
            .memberId(MEMBER_ID)
            .martId(MART_ID)
            .productAmount(productAmount)
            .deliveryFee(deliveryFee)
            .usedPoint(usedPoint)
            .payAmount(payAmount)
            .pointRate(POINT_RATE)
            .build();
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        return order;
    }
}
