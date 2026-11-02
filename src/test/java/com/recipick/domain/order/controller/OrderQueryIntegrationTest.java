package com.recipick.domain.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.repository.OrderItemRepository;
import com.recipick.domain.order.repository.OrderRepository;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

class OrderQueryIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;

    private Member member;
    private Member otherMember;
    private String token;
    private final List<Order> orders = new ArrayList<>();
    private final List<OrderItem> orderItems = new ArrayList<>();

    @BeforeEach
    void setUp() {
        member = member();
        otherMember = member();
        token = tokens.createMemberTokens(member.getId()).accessToken();
    }

    @AfterEach
    void cleanUp() {
        orderItemRepository.deleteAll(orderItems);
        orderRepository.deleteAll(orders);
        memberRepository.deleteAll(List.of(member, otherMember));
    }

    @Test
    @DisplayName("본인 주문 목록을 최신순으로 주문명·결제금액과 함께 페이지로 돌려준다")
    void searchOrders() throws Exception {
        // given
        Order older = order(member, OrderStatus.CANCELED, MartStatus.NONE, "애호박", "두부");
        Order newer = order(member, OrderStatus.PAID, MartStatus.RECEIVED, "재래식 된장");
        order(otherMember, OrderStatus.PAID, MartStatus.RECEIVED, "대파");

        // when
        MockHttpServletResponse response = perform("/api/orders?size=20");

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = data(response);
        assertThat(data.path("content").findValuesAsText("orderId"))
                .containsExactly(newer.getId().toString(), older.getId().toString());
        assertThat(data.path("content").findValuesAsText("orderName")).containsExactly("재래식 된장", "애호박 외 1건");
        JsonNode first = data.path("content").get(0);
        assertThat(first.path("orderNumber").asText()).isEqualTo(newer.getOrderNumber());
        assertThat(first.path("status").asText()).isEqualTo("PAID");
        assertThat(first.path("martStatus").asText()).isEqualTo("RECEIVED");
        assertThat(first.path("payAmount").asLong()).isEqualTo(8000L);
        assertThat(first.path("createdAt").asText()).endsWith("+09:00");
        assertThat(data.path("page").asInt()).isZero();
        assertThat(data.path("size").asInt()).isEqualTo(20);
        assertThat(data.path("totalElements").asLong()).isEqualTo(2);
        assertThat(data.path("totalPages").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("statusGroup으로 탭에 맞는 주문만 돌려준다")
    void searchOrders_statusGroup() throws Exception {
        // given
        Order pending = order(member, OrderStatus.PENDING_PAYMENT, MartStatus.NONE, "애호박");
        order(member, OrderStatus.PAID, MartStatus.DELIVERED, "두부");
        order(member, OrderStatus.REFUNDED, MartStatus.REJECTED, "대파");

        // when
        MockHttpServletResponse response = perform("/api/orders?statusGroup=ONGOING");

        // then
        assertThat(data(response).path("content").findValuesAsText("orderId"))
                .containsExactly(pending.getId().toString());
    }

    @Test
    @DisplayName("없는 statusGroup 값이면 입력값 오류로 거절한다")
    void searchOrders_invalidStatusGroup() throws Exception {
        // when
        MockHttpServletResponse response = perform("/api/orders?statusGroup=UNKNOWN");

        // then
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("주문이 없으면 빈 페이지를 돌려준다")
    void searchOrders_empty() throws Exception {
        // when
        MockHttpServletResponse response = perform("/api/orders");

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("content")).isEmpty();
        assertThat(data(response).path("totalElements").asLong()).isZero();
    }

    @Test
    @DisplayName("본인 주문 상세를 상품·금액·배송지·취소 가능 여부와 함께 돌려준다")
    void getOrder() throws Exception {
        // given
        Order order = order(member, OrderStatus.PENDING_PAYMENT, MartStatus.NONE, "애호박", "두부");

        // when
        MockHttpServletResponse response = perform("/api/orders/" + order.getId());

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = data(response);
        assertThat(data.path("orderId").asLong()).isEqualTo(order.getId());
        assertThat(data.path("orderNumber").asText()).isEqualTo(order.getOrderNumber());
        assertThat(data.path("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(data.path("martStatus").asText()).isEqualTo("NONE");
        assertThat(data.path("orderName").asText()).isEqualTo("애호박 외 1건");
        assertThat(data.path("cancelable").asBoolean()).isTrue();
        assertThat(data.path("items").findValuesAsText("productName")).containsExactly("애호박", "두부");
        JsonNode item = data.path("items").get(0);
        assertThat(item.path("unitPrice").asLong()).isEqualTo(2500L);
        assertThat(item.path("saleUnitAmount").asText()).isEqualTo("500.00");
        assertThat(item.path("saleUnit").asText()).isEqualTo("G");
        assertThat(item.path("quantity").asInt()).isEqualTo(2);
        assertThat(item.path("lineAmount").asLong()).isEqualTo(5000L);
        assertThat(data.path("productAmount").asLong()).isEqualTo(5000L);
        assertThat(data.path("deliveryFee").asLong()).isEqualTo(3000L);
        assertThat(data.path("usedPoint").isIntegralNumber()).isTrue();
        assertThat(data.path("usedPoint").asLong()).isZero();
        assertThat(data.path("payAmount").asLong()).isEqualTo(8000L);
        assertThat(data.path("receiverName").asText()).isEqualTo("홍길동");
        assertThat(data.path("receiverPhone").asText()).isEqualTo("01012345678");
        assertThat(data.path("address").asText()).isEqualTo("서울 종로구 삼청로 1");
        assertThat(data.path("detailAddress").asText()).isEqualTo("101호");
        assertThat(data.path("cancelReasonCode").isNull()).isTrue();
        assertThat(data.has("portonePaymentId")).isFalse();
    }

    @Test
    @DisplayName("다른 회원의 주문 상세는 없는 주문과 같이 ORDER-012로 응답한다")
    void getOrder_otherMember() throws Exception {
        // given
        Order others = order(otherMember, OrderStatus.PAID, MartStatus.RECEIVED, "애호박");

        // when
        MockHttpServletResponse response = perform("/api/orders/" + others.getId());

        // then
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(mapper.readTree(response.getContentAsString()).path("code").asText()).isEqualTo("ORDER-012");
    }

    @Test
    @DisplayName("없는 주문 상세는 ORDER-012로 응답한다")
    void getOrder_notFound() throws Exception {
        // when
        MockHttpServletResponse response = perform("/api/orders/" + Long.MAX_VALUE);

        // then
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(mapper.readTree(response.getContentAsString()).path("code").asText()).isEqualTo("ORDER-012");
    }

    private MockHttpServletResponse perform(String url) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andReturn().getResponse();
    }

    private JsonNode data(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString()).path("data");
    }

    private Member member() {
        return memberRepository.save(Member.builder()
                .email(UUID.randomUUID() + "@oq.test")
                .name("주문 조회 회원")
                .phone("010" + String.format("%08d", ThreadLocalRandom.current().nextInt(100000000)))
                .passwordHash("hash")
                .martId(1L)
                .build());
    }

    // 상품마다 2,500원 × 2개. 상품금액은 첫 상품 기준 5,000원, 배송비 3,000원으로 고정해 둔다
    private Order order(Member owner, OrderStatus status, MartStatus martStatus, String... productNames) {
        Order order = orderRepository.save(Order.builder()
                .orderNumber("T" + UUID.randomUUID().toString().substring(0, 20))
                .memberId(owner.getId())
                .martId(1L)
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
