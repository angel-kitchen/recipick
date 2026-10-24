package com.recipick.domain.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.repository.OrderItemRepository;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

class OrderAdminQueryIntegrationTest extends IntegrationTestSupport {

    private static final Long SUPER_ADMIN_ID = 1L;
    private static final Long MART_ADMIN_ID = 2L;
    private static final Long MEMBER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;

    // 다른 테스트가 남긴 주문과 섞이지 않도록 마트 ID를 실행마다 새로 뽑는다
    private Long martA;
    private Long martB;
    private final List<Order> orders = new ArrayList<>();
    private final List<OrderItem> orderItems = new ArrayList<>();

    @BeforeEach
    void setUp() {
        martA = ThreadLocalRandom.current().nextLong(1_000_000L, 2_000_000L);
        martB = martA + 1;
    }

    @AfterEach
    void cleanUp() {
        orderItemRepository.deleteAll(orderItems);
        orderRepository.deleteAll(orders);
    }

    @Test
    @DisplayName("토큰 없이 호출하면 401로 거절한다")
    void searchOrders_noToken() throws Exception {
        // when
        MockHttpServletResponse response = mockMvc.perform(get("/api/admin/orders")).andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("회원 토큰으로 호출하면 403 AUTH-004로 거절한다")
    void searchOrders_memberToken() throws Exception {
        // when
        MockHttpServletResponse response = perform("/api/admin/orders", memberToken());

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(code(response)).isEqualTo("AUTH-004");
    }

    @Test
    @DisplayName("SUPER_ADMIN이 martId를 지정하면 그 마트의 결제 후 주문만 최신순으로 돌려주고 결제 전 주문은 뺀다")
    void searchOrders_superAdminByMart() throws Exception {
        // given
        Order older = order(martA, OrderStatus.PAID, MartStatus.RECEIVED, "애호박", "두부");
        Order newer = order(martA, OrderStatus.PAID, MartStatus.PREPARING, "재래식 된장");
        order(martA, OrderStatus.PENDING_PAYMENT, MartStatus.NONE, "대파");
        order(martB, OrderStatus.PAID, MartStatus.RECEIVED, "양파");

        // when
        MockHttpServletResponse response = perform("/api/admin/orders?martId=" + martA + "&size=20", superToken());

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = data(response);
        assertThat(data.path("content").findValuesAsText("orderId"))
                .containsExactly(newer.getId().toString(), older.getId().toString());
        assertThat(data.path("content").findValuesAsText("orderName")).containsExactly("재래식 된장", "애호박 외 1건");
        JsonNode first = data.path("content").get(0);
        assertThat(first.path("orderNumber").asText()).isEqualTo(newer.getOrderNumber());
        assertThat(first.path("status").asText()).isEqualTo("PAID");
        assertThat(first.path("martStatus").asText()).isEqualTo("PREPARING");
        assertThat(first.path("payAmount").asLong()).isEqualTo(8000L);
        assertThat(first.path("receiverName").asText()).isEqualTo("홍길동");
        assertThat(first.path("martId").asLong()).isEqualTo(martA);
        assertThat(first.path("createdAt").asText()).endsWith("+09:00");
        assertThat(data.path("page").asInt()).isZero();
        assertThat(data.path("size").asInt()).isEqualTo(20);
        assertThat(data.path("totalElements").asLong()).isEqualTo(2);
        assertThat(data.path("totalPages").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("SUPER_ADMIN이 martId를 생략하면 전체 마트 주문을 최신순으로 돌려준다")
    void searchOrders_superAdminAllMarts() throws Exception {
        // given
        Order ofMartA = order(martA, OrderStatus.PAID, MartStatus.RECEIVED, "애호박");
        Order ofMartB = order(martB, OrderStatus.PAID, MartStatus.RECEIVED, "양파");

        // when
        MockHttpServletResponse response = perform("/api/admin/orders?size=100", superToken());

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("content").findValuesAsText("orderId"))
                .containsSubsequence(ofMartB.getId().toString(), ofMartA.getId().toString());
    }

    @Test
    @DisplayName("SUPER_ADMIN이 없는 마트 ID를 지정하면 빈 페이지를 돌려준다")
    void searchOrders_unknownMart() throws Exception {
        // given
        order(martA, OrderStatus.PAID, MartStatus.RECEIVED, "애호박");

        // when
        MockHttpServletResponse response = perform("/api/admin/orders?martId=" + (martB + 1), superToken());

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("content")).isEmpty();
        assertThat(data(response).path("totalElements").asLong()).isZero();
    }

    @Test
    @DisplayName("MART_ADMIN은 martId를 생략해도 본인 마트 주문만 돌려준다")
    void searchOrders_martAdmin() throws Exception {
        // given
        Order mine = order(martA, OrderStatus.PAID, MartStatus.RECEIVED, "애호박");
        order(martB, OrderStatus.PAID, MartStatus.RECEIVED, "양파");

        // when
        MockHttpServletResponse response = perform("/api/admin/orders", martAdminToken(martA));

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("content").findValuesAsText("orderId"))
                .containsExactly(mine.getId().toString());
    }

    @Test
    @DisplayName("MART_ADMIN이 다른 마트를 지정해 조회하면 403 AUTH-005로 거절한다")
    void searchOrders_martAdminOtherMart() throws Exception {
        // given
        order(martB, OrderStatus.PAID, MartStatus.RECEIVED, "양파");

        // when
        MockHttpServletResponse response = perform("/api/admin/orders?martId=" + martB, martAdminToken(martA));

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(code(response)).isEqualTo("AUTH-005");
    }

    @Test
    @DisplayName("martStatus로 처리 단계에 맞는 주문만 돌려준다")
    void searchOrders_martStatus() throws Exception {
        // given
        order(martA, OrderStatus.PAID, MartStatus.RECEIVED, "애호박");
        Order preparing = order(martA, OrderStatus.PAID, MartStatus.PREPARING, "두부");

        // when
        MockHttpServletResponse response = perform(
                "/api/admin/orders?martStatus=PREPARING", martAdminToken(martA));

        // then
        assertThat(data(response).path("content").findValuesAsText("orderId"))
                .containsExactly(preparing.getId().toString());
    }

    @Test
    @DisplayName("없는 martStatus 값이면 입력값 오류로 거절한다")
    void searchOrders_invalidMartStatus() throws Exception {
        // when
        MockHttpServletResponse response = perform("/api/admin/orders?martStatus=UNKNOWN", superToken());

        // then
        assertThat(response.getStatus()).isEqualTo(400);
    }

    private MockHttpServletResponse perform(String url, String token) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andReturn().getResponse();
    }

    private JsonNode data(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString()).path("data");
    }

    private String code(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString()).path("code").asText();
    }

    private String superToken() {
        return tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, SUPER_ADMIN_ID, "SUPER_ADMIN", null))
                .accessToken();
    }

    private String martAdminToken(Long martId) {
        return tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, MART_ADMIN_ID, "MART_ADMIN", martId))
                .accessToken();
    }

    private String memberToken() {
        return tokens.createMemberTokens(Long.MAX_VALUE).accessToken();
    }

    // 상품마다 2,500원 × 2개. 상품금액은 첫 상품 기준 5,000원, 배송비 3,000원으로 고정해 둔다
    private Order order(Long martId, OrderStatus status, MartStatus martStatus, String... productNames) {
        Order order = orderRepository.save(Order.builder()
                .orderNumber("T" + UUID.randomUUID().toString().substring(0, 20))
                .memberId(MEMBER_ID)
                .martId(martId)
                .status(status)
                .martStatus(martStatus)
                .productAmount(5000L)
                .deliveryFee(3000L)
                .usedPoint(0L)
                .payAmount(8000L)
                .receiverName("홍길동")
                .receiverPhone("01012345678")
                .address("서울 종로구 삼청로 1")
                .detailAddress("101호")
                .build());
        orders.add(order);
        for (String productName : productNames) {
            orderItems.add(orderItemRepository.save(OrderItem.builder()
                    .order(order)
                    .productId(1L)
                    .productName(productName)
                    .unitPrice(2500L)
                    .saleUnitAmount(new BigDecimal("500"))
                    .saleUnit(SaleUnit.G)
                    .quantity(2)
                    .lineAmount(5000L)
                    .build()));
        }
        return order;
    }
}
