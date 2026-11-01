package com.recipick.domain.order.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.domain.member.service.RegionService;
import com.recipick.domain.order.dto.request.OrderPreviewRequest;
import com.recipick.domain.order.dto.response.CartItemResponse;
import com.recipick.domain.order.dto.response.CartItemStatus;
import com.recipick.domain.order.dto.response.OrderPreviewResponse;
import com.recipick.domain.order.dto.response.OrderPreviewResponse.Delivery;
import com.recipick.domain.order.dto.response.OrderPreviewResponse.DeliveryStatus;
import com.recipick.domain.order.dto.response.OrderPreviewResponse.PendingOrder;
import com.recipick.domain.order.dto.response.OrderPreviewResponse.UnorderableReason;
import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.service.CartService;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.PaymentCreated;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.product.dto.MartResponse;
import com.recipick.domain.product.dto.OpenMartResponse;
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.entity.MartStatus;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.domain.product.service.MartService;
import com.recipick.domain.product.service.ProductService;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderQueryFacadeTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 13, 30);
    private static final Long MEMBER_ID = 1L;
    private static final Long MART_ID = 7L;
    private static final Long OTHER_MART_ID = 8L;
    private static final Long REGION_ID = 70L;
    private static final Long OTHER_REGION_ID = 80L;
    private static final String LEGAL_DONG_CODE = "1111014000";

    @Mock
    private MemberService memberService;
    @Mock
    private MembershipService membershipService;
    @Mock
    private RegionService regionService;
    @Mock
    private MartService martService;
    @Mock
    private CartService cartService;
    @Mock
    private ProductService productService;
    @Mock
    private OrderService orderService;
    @Mock
    private PaymentService paymentService;

    @Mock
    private com.recipick.global.audit.AdminAuditRecorder auditRecorder;

    private OrderQueryFacade orderQueryFacade;

    @BeforeEach
    void setUp() {
        orderQueryFacade = new OrderQueryFacade(auditRecorder, memberService, membershipService, regionService, martService, cartService,
                productService, orderService, paymentService, Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
    }

    @Test
    @DisplayName("이용 마트 지역 주소이고 모두 살 수 있으면 주문할 수 있고 상품금액·배송비·결제금액을 계산한다")
    void previewOrder() {
        // given
        givenOpenMart();
        givenRegion(REGION_ID);
        givenCart(product(10L, MART_ID, ProductStatus.SELLING, 10, 2500L),
                product(11L, MART_ID, ProductStatus.SELLING, 10, 1000L));
        givenNoPendingOrder();

        // when
        OrderPreviewResponse response = orderQueryFacade.previewOrder(MEMBER_ID, request(100L, 101L));

        // then
        assertThat(response.orderable()).isTrue();
        assertThat(response.unorderableReasons()).isEmpty();
        assertThat(response.delivery()).isEqualTo(new Delivery(DeliveryStatus.AVAILABLE, "삼청 마트"));
        assertThat(response.items()).extracting(CartItemResponse::status)
                .containsExactly(CartItemStatus.AVAILABLE, CartItemStatus.AVAILABLE);
        assertThat(response.productAmount()).isEqualTo(7000L);
        assertThat(response.deliveryFee()).isEqualTo(3000L);
        assertThat(response.payAmount()).isEqualTo(10000L);
        assertThat(response.pendingOrder()).isNull();
    }

    @Test
    @DisplayName("상품금액이 무료배송 기준 이상이면 배송비가 없다")
    void previewOrder_freeDelivery() {
        // given
        givenOpenMart();
        givenRegion(REGION_ID);
        givenCart(product(10L, MART_ID, ProductStatus.SELLING, 10, 20000L));
        givenNoPendingOrder();

        // when
        OrderPreviewResponse response = orderQueryFacade.previewOrder(MEMBER_ID, request(100L));

        // then
        assertThat(response.productAmount()).isEqualTo(40000L);
        assertThat(response.deliveryFee()).isZero();
        assertThat(response.payAmount()).isEqualTo(40000L);
    }

    @Test
    @DisplayName("멤버십 회원은 상품금액 15,000원 이상이면 배송비가 없다")
    void previewOrder_membershipFreeDelivery() {
        // given
        givenOpenMart();
        givenRegion(REGION_ID);
        givenCart(product(10L, MART_ID, ProductStatus.SELLING, 10, 7500L));
        givenNoPendingOrder();
        given(membershipService.isActive(MEMBER_ID)).willReturn(true);

        // when
        OrderPreviewResponse response = orderQueryFacade.previewOrder(MEMBER_ID, request(100L));

        // then
        assertThat(response.productAmount()).isEqualTo(15000L);
        assertThat(response.deliveryFee()).isZero();
        assertThat(response.payAmount()).isEqualTo(15000L);
    }

    @Test
    @DisplayName("구매 불가 상품은 상태로 알리고 금액에서 빼며 남은 상품으로 주문할 수 있다")
    void previewOrder_excludedItems() {
        // given
        givenOpenMart();
        givenRegion(REGION_ID);
        givenCart(product(10L, MART_ID, ProductStatus.SELLING, 10, 2500L),
                product(11L, MART_ID, ProductStatus.SELLING, 0, 1000L),
                product(12L, MART_ID, ProductStatus.STOPPED, 10, 1000L),
                product(13L, MART_ID, ProductStatus.SELLING, 1, 1000L));
        givenNoPendingOrder();

        // when
        OrderPreviewResponse response = orderQueryFacade.previewOrder(MEMBER_ID, request(100L, 101L, 102L, 103L));

        // then
        assertThat(response.orderable()).isTrue();
        assertThat(response.items()).extracting(CartItemResponse::status).containsExactly(
                CartItemStatus.AVAILABLE, CartItemStatus.SOLD_OUT, CartItemStatus.STOPPED,
                CartItemStatus.INSUFFICIENT_STOCK);
        assertThat(response.productAmount()).isEqualTo(5000L);
        assertThat(response.payAmount()).isEqualTo(8000L);
    }

    @Test
    @DisplayName("이전 마트 상품이 있으면 제외하지 않고 주문할 수 없다고 알린다")
    void previewOrder_previousMartItems() {
        // given
        givenOpenMart();
        givenRegion(REGION_ID);
        givenCart(product(10L, MART_ID, ProductStatus.SELLING, 10, 2500L),
                product(11L, OTHER_MART_ID, ProductStatus.SELLING, 10, 1000L));
        givenNoPendingOrder();

        // when
        OrderPreviewResponse response = orderQueryFacade.previewOrder(MEMBER_ID, request(100L, 101L));

        // then
        assertThat(response.orderable()).isFalse();
        assertThat(response.unorderableReasons()).containsExactly(UnorderableReason.HAS_PREVIOUS_MART_ITEMS);
        assertThat(response.items()).extracting(CartItemResponse::status)
                .containsExactly(CartItemStatus.AVAILABLE, CartItemStatus.PREVIOUS_MART);
    }

    @Test
    @DisplayName("살 수 있는 상품이 하나도 없으면 주문할 수 없고 금액은 모두 0이다")
    void previewOrder_noPurchasableItems() {
        // given
        givenOpenMart();
        givenRegion(REGION_ID);
        givenCart(product(10L, MART_ID, ProductStatus.SELLING, 0, 2500L));
        givenNoPendingOrder();

        // when
        OrderPreviewResponse response = orderQueryFacade.previewOrder(MEMBER_ID, request(100L));

        // then
        assertThat(response.unorderableReasons()).containsExactly(UnorderableReason.NO_PURCHASABLE_ITEMS);
        assertThat(response.productAmount()).isZero();
        assertThat(response.deliveryFee()).isZero();
        assertThat(response.payAmount()).isZero();
    }

    @Test
    @DisplayName("다른 마트 지역 주소이면 그 지역의 운영 중인 마트 이름과 함께 배송할 수 없다고 알린다")
    void previewOrder_otherMartArea() {
        // given
        givenOpenMart();
        givenRegion(OTHER_REGION_ID);
        given(martService.findOpenMarts(OTHER_REGION_ID))
                .willReturn(List.of(new OpenMartResponse(OTHER_MART_ID, "하안 마트", OTHER_REGION_ID)));
        givenCart(product(10L, MART_ID, ProductStatus.SELLING, 10, 2500L));
        givenNoPendingOrder();

        // when
        OrderPreviewResponse response = orderQueryFacade.previewOrder(MEMBER_ID, request(100L));

        // then
        assertThat(response.orderable()).isFalse();
        assertThat(response.unorderableReasons()).containsExactly(UnorderableReason.DELIVERY_UNAVAILABLE);
        assertThat(response.delivery()).isEqualTo(new Delivery(DeliveryStatus.OTHER_MART_AREA, "하안 마트"));
    }

    @Test
    @DisplayName("지역 목록에 없는 법정동이면 서비스 지역 밖이다")
    void previewOrder_unknownRegion() {
        // given
        givenOpenMart();
        given(regionService.getRegionIdByLegalDongCode(LEGAL_DONG_CODE)).willReturn(Optional.empty());
        givenCart(product(10L, MART_ID, ProductStatus.SELLING, 10, 2500L));
        givenNoPendingOrder();

        // when
        OrderPreviewResponse response = orderQueryFacade.previewOrder(MEMBER_ID, request(100L));

        // then
        assertThat(response.delivery()).isEqualTo(new Delivery(DeliveryStatus.OUTSIDE_SERVICE_AREA, null));
        assertThat(response.unorderableReasons()).containsExactly(UnorderableReason.DELIVERY_UNAVAILABLE);
    }

    @Test
    @DisplayName("지역은 있지만 운영 중인 마트가 없으면 서비스 지역 밖이다")
    void previewOrder_regionWithoutOpenMart() {
        // given
        givenOpenMart();
        givenRegion(OTHER_REGION_ID);
        given(martService.findOpenMarts(OTHER_REGION_ID)).willReturn(List.of());
        givenCart(product(10L, MART_ID, ProductStatus.SELLING, 10, 2500L));
        givenNoPendingOrder();

        // when
        OrderPreviewResponse response = orderQueryFacade.previewOrder(MEMBER_ID, request(100L));

        // then
        assertThat(response.delivery()).isEqualTo(new Delivery(DeliveryStatus.OUTSIDE_SERVICE_AREA, null));
    }

    @Test
    @DisplayName("이용 마트가 없으면 오류 대신 사유로 알리고 담긴 상품은 모두 이전 마트 상품이다")
    void previewOrder_martNotSelected() {
        // given
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.empty());
        givenRegion(REGION_ID);
        given(martService.findOpenMarts(REGION_ID))
                .willReturn(List.of(new OpenMartResponse(MART_ID, "삼청 마트", REGION_ID)));
        givenCart(product(10L, MART_ID, ProductStatus.SELLING, 10, 2500L));
        givenNoPendingOrder();

        // when
        OrderPreviewResponse response = orderQueryFacade.previewOrder(MEMBER_ID, request(100L));

        // then
        assertThat(response.unorderableReasons()).containsExactly(
                UnorderableReason.MART_NOT_SELECTED, UnorderableReason.DELIVERY_UNAVAILABLE,
                UnorderableReason.HAS_PREVIOUS_MART_ITEMS, UnorderableReason.NO_PURCHASABLE_ITEMS);
        assertThat(response.delivery().status()).isEqualTo(DeliveryStatus.OTHER_MART_AREA);
    }

    @Test
    @DisplayName("이용 마트가 운영 중이 아니면 오류 대신 사유로 알린다")
    void previewOrder_martNotOpen() {
        // given
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.of(MART_ID));
        given(martService.findMart(MART_ID)).willReturn(Optional.of(mart(MartStatus.CLOSED)));
        givenRegion(REGION_ID);
        given(martService.findOpenMarts(REGION_ID)).willReturn(List.of());
        givenCart(product(10L, MART_ID, ProductStatus.SELLING, 10, 2500L));
        givenNoPendingOrder();

        // when
        OrderPreviewResponse response = orderQueryFacade.previewOrder(MEMBER_ID, request(100L));

        // then
        assertThat(response.unorderableReasons())
                .containsExactly(UnorderableReason.MART_NOT_OPEN, UnorderableReason.DELIVERY_UNAVAILABLE);
    }

    @Test
    @DisplayName("결제 대기 주문이 있으면 주문번호·결제 ID·주문명·금액·만료 시각과 이어서 결제 가능 여부를 알린다")
    void previewOrder_pendingOrder() {
        // given
        givenOpenMart();
        givenRegion(REGION_ID);
        givenCart(product(10L, MART_ID, ProductStatus.SELLING, 10, 2500L));
        givenPendingOrder(NOW.plusMinutes(3));

        // when
        OrderPreviewResponse response = orderQueryFacade.previewOrder(MEMBER_ID, request(100L));

        // then
        assertThat(response.orderable()).isFalse();
        assertThat(response.unorderableReasons()).containsExactly(UnorderableReason.PENDING_ORDER_EXISTS);
        assertThat(response.pendingOrder()).isEqualTo(new PendingOrder(
                500L, "20261023-ABCDE12345", 600L, "양파 외 1건", 12300L, NOW.plusMinutes(3), true));
    }

    @Test
    @DisplayName("결제 대기 주문의 만료 시각이 지났으면 이어서 결제할 수 없다")
    void previewOrder_pendingOrderExpired() {
        // given
        givenOpenMart();
        givenRegion(REGION_ID);
        givenCart(product(10L, MART_ID, ProductStatus.SELLING, 10, 2500L));
        givenPendingOrder(NOW);

        // when
        OrderPreviewResponse response = orderQueryFacade.previewOrder(MEMBER_ID, request(100L));

        // then
        assertThat(response.pendingOrder().payable()).isFalse();
    }

    @Test
    @DisplayName("법정동 코드 형식이 틀리면 INVALID_DELIVERY_INFORMATION")
    void previewOrder_invalidLegalDongCode() {
        // when, then
        assertErrorCode(new OrderPreviewRequest(List.of(100L), "11110"), OrderErrorCode.INVALID_DELIVERY_INFORMATION);
        assertErrorCode(new OrderPreviewRequest(List.of(100L), null), OrderErrorCode.INVALID_DELIVERY_INFORMATION);
        verifyNoInteractions(memberService, cartService);
    }

    @Test
    @DisplayName("같은 장바구니 항목을 두 번 고르면 DUPLICATE_ORDER_ITEM")
    void previewOrder_duplicateItems() {
        // when, then
        assertErrorCode(request(100L, 100L), OrderErrorCode.DUPLICATE_ORDER_ITEM);
        verifyNoInteractions(memberService, cartService);
    }

    @Test
    @DisplayName("없거나 다른 회원의 장바구니 항목이면 CART_ITEM_NOT_FOUND")
    void previewOrder_cartItemNotFound() {
        // given
        givenOpenMart();
        givenRegion(REGION_ID);
        given(cartService.findCartItems(MEMBER_ID, List.of(100L)))
                .willThrow(new BusinessException(OrderErrorCode.CART_ITEM_NOT_FOUND));

        // when, then
        assertErrorCode(request(100L), OrderErrorCode.CART_ITEM_NOT_FOUND);
    }

    private void assertErrorCode(OrderPreviewRequest request, ErrorCode errorCode) {
        assertThatThrownBy(() -> orderQueryFacade.previewOrder(MEMBER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    private OrderPreviewRequest request(Long... cartItemIds) {
        return new OrderPreviewRequest(Arrays.asList(cartItemIds), LEGAL_DONG_CODE);
    }

    private void givenOpenMart() {
        given(memberService.getMartId(MEMBER_ID)).willReturn(Optional.of(MART_ID));
        given(martService.findMart(MART_ID)).willReturn(Optional.of(mart(MartStatus.OPEN)));
    }

    private void givenRegion(Long regionId) {
        given(regionService.getRegionIdByLegalDongCode(LEGAL_DONG_CODE)).willReturn(Optional.of(regionId));
    }

    // 장바구니 항목 ID는 100부터, 수량은 모두 2개
    private void givenCart(ProductSummary... products) {
        List<CartItem> cartItems = new ArrayList<>();
        for (int i = 0; i < products.length; i++) {
            CartItem cartItem = CartItem.builder()
                    .memberId(MEMBER_ID)
                    .productId(products[i].productId())
                    .quantity(2)
                    .build();
            ReflectionTestUtils.setField(cartItem, "id", 100L + i);
            cartItems.add(cartItem);
        }
        given(cartService.findCartItems(eq(MEMBER_ID), anyList())).willReturn(cartItems);
        given(productService.findProducts(cartItems.stream().map(CartItem::getProductId).toList()))
                .willReturn(List.of(products));
    }

    private void givenNoPendingOrder() {
        given(orderService.findPendingOrder(MEMBER_ID)).willReturn(Optional.empty());
    }

    private void givenPendingOrder(LocalDateTime expiresAt) {
        Order order = Order.builder().orderNumber("20261023-ABCDE12345").memberId(MEMBER_ID).build();
        ReflectionTestUtils.setField(order, "id", 500L);
        given(orderService.findPendingOrder(MEMBER_ID)).willReturn(Optional.of(order));
        given(orderService.findOrderItems(500L)).willReturn(List.of(
                OrderItem.builder().order(order).productName("양파").build(),
                OrderItem.builder().order(order).productName("대파").build()));
        given(paymentService.getCreatedByOrderId(500L))
                .willReturn(new PaymentCreated(600L, "pay-1", 12300L, expiresAt));
    }

    private MartResponse mart(MartStatus status) {
        return new MartResponse(MART_ID, REGION_ID, "삼청 마트", null, null, 2000, status);
    }

    private ProductSummary product(Long productId, Long martId, ProductStatus status, int stock, Long price) {
        return new ProductSummary(productId, martId, "상품" + productId, null, price, null, null, status, stock, false);
    }
    @Test
    @DisplayName("관리자 주문 목록 감사 기록이 실패하면 조회 결과를 반환하지 않는다")
    void refusesAdminResponseWhenAuditFails() {
        // given
        var request = new com.recipick.domain.order.dto.request.OrderAdminSearchRequest(null, null);
        var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        given(orderService.searchAdminOrders(com.recipick.domain.member.entity.enums.AdminRole.SUPER_ADMIN,
                null, request, pageable)).willReturn(new com.recipick.global.response.PageResponse<>(List.of(), 0, 20, 0, 0));
        org.mockito.Mockito.doThrow(new IllegalStateException("감사 저장 실패"))
                .when(auditRecorder).record(org.mockito.ArgumentMatchers.any());
        // when, then
        assertThatThrownBy(() -> orderQueryFacade.searchAdminOrders(
                com.recipick.domain.member.entity.enums.AdminRole.SUPER_ADMIN, null, request, pageable))
                .isInstanceOf(IllegalStateException.class).hasMessage("감사 저장 실패");
    }

}
