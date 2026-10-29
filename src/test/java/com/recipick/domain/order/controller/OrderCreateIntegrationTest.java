package com.recipick.domain.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.order.dto.request.OrderCreateRequest;
import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.entity.OrderStatusHistory;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.entity.enums.OrderStatusActor;
import com.recipick.domain.order.repository.CartItemRepository;
import com.recipick.domain.order.repository.OrderItemRepository;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.repository.PaymentRepository;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.security.JwtTokenProvider;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

// 실패 시 주문·재고가 롤백되는지 확인해야 해서 테스트 트랜잭션으로 감싸지 않고 만든 데이터를 직접 지운다
class OrderCreateIntegrationTest extends IntegrationTestSupport {

    // data.sql 시연 데이터: 1번 마트는 삼청동(1111014000), 2번 마트는 하안동(4121010300)
    private static final Long MART_ID = 1L;
    private static final Long OTHER_MART_ID = 2L;
    private static final String MART_LEGAL_DONG_CODE = "1111014000";
    private static final String OTHER_MART_LEGAL_DONG_CODE = "4121010300";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MartRepository martRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CartItemRepository cartItemRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member member;
    private String token;
    private final List<Product> products = new ArrayList<>();
    private final List<CartItem> cartItems = new ArrayList<>();

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.builder()
            .email(UUID.randomUUID() + "@order.test")
            .name("주문 회원")
            .phone("010" + String.format("%08d", ThreadLocalRandom.current().nextInt(100000000)))
            .passwordHash("hash")
            .martId(MART_ID)
            .build());
        token = tokens.createMemberTokens(member.getId()).accessToken();
    }

    @AfterEach
    void cleanUp() {
        deletePoints(member.getId());
        for (Long orderId : orderRepository.findIdsByMemberId(member.getId())) {
            paymentRepository.findByOrderId(orderId).ifPresent(paymentRepository::delete);
            orderStatusHistoryRepository.deleteAll(orderStatusHistoryRepository.findAllByOrderId(orderId));
            orderItemRepository.deleteAll(orderItemRepository.findAllByOrderId(orderId));
            orderRepository.deleteById(orderId);
        }
        cartItemRepository.deleteAll(cartItems);
        productRepository.deleteAll(products);
        memberRepository.delete(member);
    }

    // 주문 생성이 만든 적립 예정·포인트 이력을 외래키 역순(이력 → 사용 → 적립 → 회원 포인트)으로 지운다
    private void deletePoints(Long memberId) {
        jdbcTemplate.update("DELETE FROM point_history WHERE member_id = ?", memberId);
        jdbcTemplate.update("DELETE pu FROM point_usage pu JOIN point_earning pe ON pu.point_earning_id = pe.id "
            + "WHERE pe.member_id = ?", memberId);
        jdbcTemplate.update("DELETE FROM point_earning WHERE member_id = ?", memberId);
        jdbcTemplate.update("DELETE FROM member_point WHERE member_id = ?", memberId);
    }

    @Test
    @DisplayName("장바구니에서 고른 상품으로 결제 대기 주문과 진행 중 결제를 만들고 재고를 차감한다")
    void createOrder() throws Exception {
        // given
        Product zucchini = product(MART_ID, "애호박", 1500L, 10);
        Product tofu = product(MART_ID, "두부", 2000L, 5);
        CartItem first = cartItem(zucchini, 2);
        CartItem second = cartItem(tofu, 1);

        // when
        MockHttpServletResponse response = createOrder("key-1", request(List.of(first.getId(), second.getId())));

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        JsonNode data = data(response);
        assertThat(data.path("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(data.path("orderNumber").asText()).matches("\\d{8}-[0-9A-Z]{10}");
        assertThat(data.path("orderName").asText()).isEqualTo("애호박 외 1건");
        assertThat(data.path("productAmount").asLong()).isEqualTo(5000L);
        assertThat(data.path("deliveryFee").asLong()).isEqualTo(3000L);
        assertThat(data.path("amount").asLong()).isEqualTo(8000L);
        assertThat(data.path("portonePaymentId").asText()).isNotBlank();
        assertThat(data.path("expiresAt").asText()).endsWith("+09:00");
        assertThat(data.path("receiverName").asText()).isEqualTo("홍길동");
        assertThat(data.path("receiverPhone").asText()).isEqualTo("01012345678");

        Order order = orderRepository.findById(data.path("orderId").asLong()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.NONE);
        assertThat(order.getPendingMemberId()).isEqualTo(member.getId());
        assertThat(order.getMartId()).isEqualTo(MART_ID);
        assertThat(order.getRegionId()).isEqualTo(1L);
        assertThat(order.getUsedPoint()).isZero();
        assertThat(order.getPayAmount()).isEqualTo(8000L);

        List<OrderItem> items = orderItemRepository.findAllByOrderIdOrderByIdAsc(order.getId());
        assertThat(items).extracting(OrderItem::getProductName, OrderItem::getUnitPrice, OrderItem::getQuantity,
                OrderItem::getLineAmount)
            .containsExactly(
                tuple("애호박", 1500L, 2, 3000L),
                tuple("두부", 2000L, 1, 2000L));

        List<OrderStatusHistory> histories = orderStatusHistoryRepository.findAllByOrderId(order.getId());
        assertThat(histories).singleElement().satisfies(history -> {
            assertThat(history.getFromStatus()).isNull();
            assertThat(history.getToStatus()).isEqualTo("PENDING_PAYMENT");
            assertThat(history.getActor()).isEqualTo(OrderStatusActor.MEMBER);
        });

        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(payment.getAmount()).isEqualTo(8000L);
        assertThat(payment.getId()).isEqualTo(data.path("paymentId").asLong());

        assertThat(stockOf(zucchini)).isEqualTo(8);
        assertThat(stockOf(tofu)).isEqualTo(4);
    }

    @Test
    @DisplayName("같은 키로 같은 요청을 다시 보내면 기존 주문을 200으로 돌려주고 재고는 다시 차감하지 않는다")
    void createOrder_sameKeyRetry() throws Exception {
        // given
        Product zucchini = product(MART_ID, "애호박", 1500L, 10);
        CartItem cartItem = cartItem(zucchini, 2);
        MockHttpServletResponse first = createOrder("Key-Retry", request(List.of(cartItem.getId())));

        // when
        MockHttpServletResponse retry = createOrder("key-retry", request(List.of(cartItem.getId())));

        // then
        assertThat(retry.getStatus()).isEqualTo(200);
        assertThat(data(retry).path("orderId").asLong()).isEqualTo(data(first).path("orderId").asLong());
        assertThat(data(retry).path("portonePaymentId").asText())
            .isEqualTo(data(first).path("portonePaymentId").asText());
        assertThat(data(retry).path("orderName").asText()).isEqualTo("애호박");
        assertThat(orderRepository.findIdsByMemberId(member.getId())).hasSize(1);
        assertThat(stockOf(zucchini)).isEqualTo(8);
    }

    @Test
    @DisplayName("같은 키로 다른 요청을 보내면 COMMON-011로 거절한다")
    void createOrder_sameKeyDifferentRequest() throws Exception {
        // given
        Product zucchini = product(MART_ID, "애호박", 1500L, 10);
        CartItem cartItem = cartItem(zucchini, 2);
        createOrder("key-1", request(List.of(cartItem.getId())));
        OrderCreateRequest changed = new OrderCreateRequest(List.of(cartItem.getId()), "김철수", "01012345678",
            "서울 종로구 삼청로 1", "101호", MART_LEGAL_DONG_CODE);

        // when
        MockHttpServletResponse response = createOrder("key-1", changed);

        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(code(response)).isEqualTo("COMMON-011");
    }

    @Test
    @DisplayName("결제 대기 주문이 있으면 새 키로 주문해도 ORDER-003으로 거절한다")
    void createOrder_pendingOrderExists() throws Exception {
        // given
        Product zucchini = product(MART_ID, "애호박", 1500L, 10);
        CartItem cartItem = cartItem(zucchini, 2);
        createOrder("key-1", request(List.of(cartItem.getId())));

        // when
        MockHttpServletResponse response = createOrder("key-2", request(List.of(cartItem.getId())));

        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(code(response)).isEqualTo("ORDER-003");
        assertThat(stockOf(zucchini)).isEqualTo(8);
    }

    @Test
    @DisplayName("배송지 법정동이 이용 마트 지역과 다르면 ORDER-008로 거절하고 재고를 차감하지 않는다")
    void createOrder_outsideDeliveryArea() throws Exception {
        // given
        Product zucchini = product(MART_ID, "애호박", 1500L, 10);
        CartItem cartItem = cartItem(zucchini, 2);
        OrderCreateRequest request = new OrderCreateRequest(List.of(cartItem.getId()), "홍길동", "01012345678",
            "경기 광명시 하안로 1", null, OTHER_MART_LEGAL_DONG_CODE);

        // when
        MockHttpServletResponse response = createOrder("key-1", request);

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(code(response)).isEqualTo("ORDER-008");
        assertThat(stockOf(zucchini)).isEqualTo(10);
    }

    @Test
    @DisplayName("법정동 코드 형식이 틀리면 ORDER-006으로 거절한다")
    void createOrder_invalidLegalDongCode() throws Exception {
        // given
        Product zucchini = product(MART_ID, "애호박", 1500L, 10);
        CartItem cartItem = cartItem(zucchini, 2);
        OrderCreateRequest request = new OrderCreateRequest(List.of(cartItem.getId()), "홍길동", "01012345678",
            "서울 종로구 삼청로 1", null, "11110");

        // when
        MockHttpServletResponse response = createOrder("key-1", request);

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(code(response)).isEqualTo("ORDER-006");
    }

    @Test
    @DisplayName("이전 마트 상품이 섞여 있으면 CART-006으로 거절하고 재고를 차감하지 않는다")
    void createOrder_previousMartItem() throws Exception {
        // given
        Product zucchini = product(MART_ID, "애호박", 1500L, 10);
        Product otherMartTofu = product(OTHER_MART_ID, "두부", 2000L, 5);
        CartItem first = cartItem(zucchini, 2);
        CartItem second = cartItem(otherMartTofu, 1);

        // when
        MockHttpServletResponse response = createOrder("key-1", request(List.of(first.getId(), second.getId())));

        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(code(response)).isEqualTo("CART-006");
        assertThat(stockOf(zucchini)).isEqualTo(10);
    }

    @Test
    @DisplayName("다른 회원의 장바구니 항목이 있으면 CART-001로 거절한다")
    void createOrder_otherMembersCartItem() throws Exception {
        // given
        Product zucchini = product(MART_ID, "애호박", 1500L, 10);
        CartItem cartItem = cartItem(zucchini, 2);

        // when
        MockHttpServletResponse response = createOrder("key-1", request(List.of(cartItem.getId(), Long.MAX_VALUE)));

        // then
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(code(response)).isEqualTo("CART-001");
    }

    @Test
    @DisplayName("한 상품이라도 재고가 부족하면 STOCK-001로 거절하고 주문·결제·재고 변경을 모두 롤백한다")
    void createOrder_insufficientStock() throws Exception {
        // given
        Product zucchini = product(MART_ID, "애호박", 1500L, 10);
        Product tofu = product(MART_ID, "두부", 2000L, 1);
        CartItem first = cartItem(zucchini, 2);
        CartItem second = cartItem(tofu, 2);

        // when
        MockHttpServletResponse response = createOrder("key-1", request(List.of(first.getId(), second.getId())));

        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(code(response)).isEqualTo("STOCK-001");
        assertThat(orderRepository.findIdsByMemberId(member.getId())).isEmpty();
        assertThat(stockOf(zucchini)).isEqualTo(10);
        assertThat(stockOf(tofu)).isEqualTo(1);
    }

    @Test
    @DisplayName("판매 중지 상품이 있으면 PRODUCT-003으로 거절한다")
    void createOrder_productNotSelling() throws Exception {
        // given
        Product zucchini = product(MART_ID, "애호박", 1500L, 10);
        zucchini.changeStatus(ProductStatus.STOPPED);
        productRepository.save(zucchini);
        CartItem cartItem = cartItem(zucchini, 2);

        // when
        MockHttpServletResponse response = createOrder("key-1", request(List.of(cartItem.getId())));

        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(code(response)).isEqualTo("PRODUCT-003");
        assertThat(orderRepository.findIdsByMemberId(member.getId())).isEmpty();
    }

    @Test
    @DisplayName("Idempotency-Key 헤더가 없으면 COMMON-007로 거절한다")
    void createOrder_missingIdempotencyKey() throws Exception {
        // given
        Product zucchini = product(MART_ID, "애호박", 1500L, 10);
        CartItem cartItem = cartItem(zucchini, 2);

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request(List.of(cartItem.getId())))))
            .andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(code(response)).isEqualTo("COMMON-007");
    }

    private OrderCreateRequest request(List<Long> cartItemIds) {
        return new OrderCreateRequest(cartItemIds, "홍길동", "01012345678", "서울 종로구 삼청로 1", "101호",
            MART_LEGAL_DONG_CODE);
    }

    private MockHttpServletResponse createOrder(String idempotencyKey, OrderCreateRequest request) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)))
            .andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        return response;
    }

    private Product product(Long martId, String name, Long price, int stock) {
        Mart mart = martRepository.findById(martId).orElseThrow();
        Product product = productRepository.save(Product.builder()
            .mart(mart)
            .name(name)
            .price(price)
            .saleUnitAmount(new BigDecimal("1"))
            .saleUnit(SaleUnit.EA)
            .totalQuantity(stock)
            .build());
        products.add(product);
        return product;
    }

    private CartItem cartItem(Product product, int quantity) {
        CartItem cartItem = cartItemRepository.save(CartItem.builder()
            .memberId(member.getId())
            .productId(product.getId())
            .quantity(quantity)
            .build());
        cartItems.add(cartItem);
        return cartItem;
    }

    private int stockOf(Product product) {
        return productRepository.findById(product.getId()).orElseThrow().getTotalQuantity();
    }

    private JsonNode data(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString()).path("data");
    }

    private String code(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString()).path("code").asText();
    }
}
