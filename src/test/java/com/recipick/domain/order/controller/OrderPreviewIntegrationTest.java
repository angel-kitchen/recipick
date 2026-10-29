package com.recipick.domain.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.order.dto.request.OrderCreateRequest;
import com.recipick.domain.order.dto.request.OrderPreviewRequest;
import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.order.repository.CartItemRepository;
import com.recipick.domain.order.repository.OrderItemRepository;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
import com.recipick.domain.payment.repository.PaymentRepository;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
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

// 결제 대기 안내를 실제 주문 생성으로 확인해야 해서 테스트 트랜잭션으로 감싸지 않고 만든 데이터를 직접 지운다
class OrderPreviewIntegrationTest extends IntegrationTestSupport {

    // data.sql 시연 데이터: 1번 마트(삼청 마트)는 삼청동, 2번 마트(하안 마트)는 하안동
    private static final Long MART_ID = 1L;
    private static final Long OTHER_MART_ID = 2L;
    private static final String MART_LEGAL_DONG_CODE = "1111014000";
    private static final String OTHER_MART_LEGAL_DONG_CODE = "4121010300";
    private static final String UNKNOWN_LEGAL_DONG_CODE = "9999999999";

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
            .email(UUID.randomUUID() + "@op.test")
            .name("미리보기 회원")
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
    @DisplayName("이용 마트 지역 주소이면 주문할 수 있고 살 수 없는 상품은 상태로 알리며 금액에서 뺀다")
    void previewOrder() throws Exception {
        // given
        CartItem soybeanPaste = cartItem(product(MART_ID, "재래식 된장", 4500L, 10), 2);
        CartItem tofu = cartItem(product(MART_ID, "두부", 2000L, 1), 2);

        // when
        MockHttpServletResponse response = preview(List.of(soybeanPaste.getId(), tofu.getId()), MART_LEGAL_DONG_CODE);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = data(response);
        assertThat(data.path("orderable").asBoolean()).isTrue();
        assertThat(texts(data.path("unorderableReasons"))).isEmpty();
        assertThat(data.path("delivery").path("status").asText()).isEqualTo("AVAILABLE");
        assertThat(data.path("delivery").path("martName").asText()).isEqualTo("삼청 마트");
        assertThat(data.path("items").findValuesAsText("status")).containsExactly("AVAILABLE", "INSUFFICIENT_STOCK");
        assertThat(data.path("items").get(0).path("productName").asText()).isEqualTo("재래식 된장");
        assertThat(data.path("productAmount").asLong()).isEqualTo(9000L);
        assertThat(data.path("deliveryFee").asLong()).isEqualTo(3000L);
        assertThat(data.path("payAmount").asLong()).isEqualTo(12000L);
        assertThat(data.path("pendingOrder").isNull()).isTrue();
    }

    @Test
    @DisplayName("다른 마트 지역 주소이면 그 마트 이름과 함께 배송할 수 없다고 알린다")
    void previewOrder_otherMartArea() throws Exception {
        // given
        CartItem cartItem = cartItem(product(MART_ID, "재래식 된장", 4500L, 10), 1);

        // when
        MockHttpServletResponse response = preview(List.of(cartItem.getId()), OTHER_MART_LEGAL_DONG_CODE);

        // then
        JsonNode data = data(response);
        assertThat(data.path("orderable").asBoolean()).isFalse();
        assertThat(texts(data.path("unorderableReasons"))).containsExactly("DELIVERY_UNAVAILABLE");
        assertThat(data.path("delivery").path("status").asText()).isEqualTo("OTHER_MART_AREA");
        assertThat(data.path("delivery").path("martName").asText()).isEqualTo("하안 마트");
    }

    @Test
    @DisplayName("서비스 지역 밖 주소이면 마트 이름 없이 배송할 수 없다고 알린다")
    void previewOrder_outsideServiceArea() throws Exception {
        // given
        CartItem cartItem = cartItem(product(MART_ID, "재래식 된장", 4500L, 10), 1);

        // when
        MockHttpServletResponse response = preview(List.of(cartItem.getId()), UNKNOWN_LEGAL_DONG_CODE);

        // then
        JsonNode delivery = data(response).path("delivery");
        assertThat(delivery.path("status").asText()).isEqualTo("OUTSIDE_SERVICE_AREA");
        assertThat(delivery.path("martName").isNull()).isTrue();
    }

    @Test
    @DisplayName("이전 마트 상품이 섞이면 제외하지 않고 주문할 수 없다고 알린다")
    void previewOrder_previousMartItems() throws Exception {
        // given
        CartItem mine = cartItem(product(MART_ID, "재래식 된장", 4500L, 10), 1);
        CartItem previous = cartItem(product(OTHER_MART_ID, "두부", 2000L, 10), 1);

        // when
        MockHttpServletResponse response = preview(List.of(mine.getId(), previous.getId()), MART_LEGAL_DONG_CODE);

        // then
        JsonNode data = data(response);
        assertThat(texts(data.path("unorderableReasons"))).containsExactly("HAS_PREVIOUS_MART_ITEMS");
        assertThat(data.path("items").findValuesAsText("status")).containsExactly("AVAILABLE", "PREVIOUS_MART");
    }

    @Test
    @DisplayName("결제 대기 주문이 있으면 그 주문의 주문번호·결제 ID·주문명·금액·만료 시각을 알린다")
    void previewOrder_pendingOrder() throws Exception {
        // given
        CartItem ordered = cartItem(product(MART_ID, "재래식 된장", 4500L, 10), 2);
        CartItem another = cartItem(product(MART_ID, "두부", 2000L, 10), 1);
        JsonNode created = data(createOrder(List.of(ordered.getId())));

        // when
        MockHttpServletResponse response = preview(List.of(another.getId()), MART_LEGAL_DONG_CODE);

        // then
        JsonNode data = data(response);
        assertThat(data.path("orderable").asBoolean()).isFalse();
        assertThat(texts(data.path("unorderableReasons"))).containsExactly("PENDING_ORDER_EXISTS");
        JsonNode pendingOrder = data.path("pendingOrder");
        assertThat(pendingOrder.path("orderId").asLong()).isEqualTo(created.path("orderId").asLong());
        assertThat(pendingOrder.path("orderNumber").asText()).isEqualTo(created.path("orderNumber").asText());
        assertThat(pendingOrder.path("paymentId").asLong()).isEqualTo(created.path("paymentId").asLong());
        assertThat(pendingOrder.path("orderName").asText()).isEqualTo("재래식 된장");
        assertThat(pendingOrder.path("amount").asLong()).isEqualTo(12000L);
        assertThat(pendingOrder.path("paymentExpiresAt").asText()).isEqualTo(created.path("expiresAt").asText());
        assertThat(pendingOrder.path("payable").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("법정동 코드 형식이 틀리면 ORDER-006으로 거절한다")
    void previewOrder_invalidLegalDongCode() throws Exception {
        // given
        CartItem cartItem = cartItem(product(MART_ID, "재래식 된장", 4500L, 10), 1);

        // when
        MockHttpServletResponse response = preview(List.of(cartItem.getId()), "11110");

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(code(response)).isEqualTo("ORDER-006");
    }

    @Test
    @DisplayName("없는 장바구니 항목이면 CART-001로 거절한다")
    void previewOrder_cartItemNotFound() throws Exception {
        // when
        MockHttpServletResponse response = preview(List.of(Long.MAX_VALUE), MART_LEGAL_DONG_CODE);

        // then
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(code(response)).isEqualTo("CART-001");
    }

    @Test
    @DisplayName("주문할 상품을 고르지 않으면 입력값 오류로 거절한다")
    void previewOrder_emptyItems() throws Exception {
        // when
        MockHttpServletResponse response = preview(List.of(), MART_LEGAL_DONG_CODE);

        // then
        assertThat(response.getStatus()).isEqualTo(400);
    }

    private MockHttpServletResponse preview(List<Long> cartItemIds, String legalDongCode) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/orders/preview")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new OrderPreviewRequest(cartItemIds, legalDongCode))))
            .andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        return response;
    }

    private MockHttpServletResponse createOrder(List<Long> cartItemIds) throws Exception {
        OrderCreateRequest request = new OrderCreateRequest(cartItemIds, "홍길동", "01012345678",
            "서울 종로구 삼청로 1", "101호", MART_LEGAL_DONG_CODE);
        MockHttpServletResponse response = mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)))
            .andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        assertThat(response.getStatus()).isEqualTo(201);
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

    private List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    private JsonNode data(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString()).path("data");
    }

    private String code(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString()).path("code").asText();
    }
}
