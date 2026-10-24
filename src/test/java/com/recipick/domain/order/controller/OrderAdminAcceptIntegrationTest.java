package com.recipick.domain.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderStatusHistory;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.entity.enums.OrderStatusActor;
import com.recipick.domain.order.entity.enums.OrderStatusType;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

class OrderAdminAcceptIntegrationTest extends IntegrationTestSupport {

    private static final Long SUPER_ADMIN_ID = 1L;
    private static final Long MART_ADMIN_ID = 2L;
    private static final Long MEMBER_ID = 7L;
    // data.sql의 시연 마트 둘(삼청 마트, 하안 마트). 주문 ID로만 조회하므로 다른 테스트와 격리할 필요가 없다
    private static final Long MART_A = 1L;
    private static final Long MART_B = 2L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderStatusHistoryRepository historyRepository;

    private final List<Order> orders = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Order order : orders) {
            historyRepository.deleteAll(historyRepository.findAllByOrderId(order.getId()));
        }
        orderRepository.deleteAll(orders);
    }

    @Test
    @DisplayName("MART_ADMIN이 접수중인 본인 마트 주문을 수락하면 배송준비중이 되고 처리 관리자가 담긴 마트 주체 이력이 남는다")
    void acceptOrder_martAdmin() throws Exception {
        // given
        Order order = order(MART_A, OrderStatus.PAID, MartStatus.RECEIVED);

        // when
        MockHttpServletResponse response = accept(order.getId(), martAdminToken(MART_A));

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = data(response);
        assertThat(data.path("orderId").asLong()).isEqualTo(order.getId());
        assertThat(data.path("status").asText()).isEqualTo("PAID");
        assertThat(data.path("martStatus").asText()).isEqualTo("PREPARING");
        Order saved = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(saved.getMartStatus()).isEqualTo(MartStatus.PREPARING);
        List<OrderStatusHistory> histories = historyRepository.findAllByOrderId(order.getId());
        assertThat(histories).hasSize(1);
        OrderStatusHistory history = histories.get(0);
        assertThat(history.getStatusType()).isEqualTo(OrderStatusType.MART);
        assertThat(history.getFromStatus()).isEqualTo("RECEIVED");
        assertThat(history.getToStatus()).isEqualTo("PREPARING");
        assertThat(history.getActor()).isEqualTo(OrderStatusActor.MART);
        assertThat(history.getChangedByAdminId()).isEqualTo(MART_ADMIN_ID);
        assertThat(history.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 주문을 다시 수락하면 200으로 현재 상태를 돌려주고 이력은 늘지 않는다")
    void acceptOrder_repeated() throws Exception {
        // given
        Order order = order(MART_A, OrderStatus.PAID, MartStatus.RECEIVED);
        accept(order.getId(), martAdminToken(MART_A));

        // when
        MockHttpServletResponse response = accept(order.getId(), martAdminToken(MART_A));

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("martStatus").asText()).isEqualTo("PREPARING");
        assertThat(historyRepository.findAllByOrderId(order.getId())).hasSize(1);
    }

    @Test
    @DisplayName("SUPER_ADMIN은 어느 마트의 주문이든 수락할 수 있고 처리 관리자로 기록된다")
    void acceptOrder_superAdmin() throws Exception {
        // given
        Order order = order(MART_B, OrderStatus.PAID, MartStatus.RECEIVED);

        // when
        MockHttpServletResponse response = accept(order.getId(), superToken());

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(historyRepository.findAllByOrderId(order.getId()))
                .extracting(OrderStatusHistory::getChangedByAdminId)
                .containsExactly(SUPER_ADMIN_ID);
    }

    @Test
    @DisplayName("MART_ADMIN이 다른 마트 주문을 수락하면 403 AUTH-005로 거절하고 상태는 그대로다")
    void acceptOrder_otherMart() throws Exception {
        // given
        Order order = order(MART_B, OrderStatus.PAID, MartStatus.RECEIVED);

        // when
        MockHttpServletResponse response = accept(order.getId(), martAdminToken(MART_A));

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(code(response)).isEqualTo("AUTH-005");
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getMartStatus())
                .isEqualTo(MartStatus.RECEIVED);
    }

    @Test
    @DisplayName("회원 취소가 진행 중인 주문을 수락하면 409 ORDER-014로 거절한다")
    void acceptOrder_cancelRequested() throws Exception {
        // given
        Order order = order(MART_A, OrderStatus.CANCEL_REQUESTED, MartStatus.RECEIVED);

        // when
        MockHttpServletResponse response = accept(order.getId(), martAdminToken(MART_A));

        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(code(response)).isEqualTo("ORDER-014");
        assertThat(historyRepository.findAllByOrderId(order.getId())).isEmpty();
    }

    @Test
    @DisplayName("이미 배송 중인 주문을 수락하면 409 ORDER-014로 거절한다")
    void acceptOrder_shipping() throws Exception {
        // given
        Order order = order(MART_A, OrderStatus.PAID, MartStatus.SHIPPING);

        // when
        MockHttpServletResponse response = accept(order.getId(), martAdminToken(MART_A));

        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(code(response)).isEqualTo("ORDER-014");
    }

    @Test
    @DisplayName("결제 전 주문을 수락하면 없는 주문과 같이 404 ORDER-012로 응답한다")
    void acceptOrder_pendingOrder() throws Exception {
        // given
        Order order = order(MART_A, OrderStatus.PENDING_PAYMENT, MartStatus.NONE);

        // when
        MockHttpServletResponse response = accept(order.getId(), martAdminToken(MART_A));

        // then
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(code(response)).isEqualTo("ORDER-012");
    }

    @Test
    @DisplayName("없는 주문을 수락하면 404 ORDER-012로 응답한다")
    void acceptOrder_notFound() throws Exception {
        // when
        MockHttpServletResponse response = accept(Long.MAX_VALUE, superToken());

        // then
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(code(response)).isEqualTo("ORDER-012");
    }

    @Test
    @DisplayName("회원 토큰으로 수락하면 403 AUTH-004로 거절한다")
    void acceptOrder_memberToken() throws Exception {
        // given
        Order order = order(MART_A, OrderStatus.PAID, MartStatus.RECEIVED);

        // when
        MockHttpServletResponse response = accept(order.getId(), tokens.createMemberTokens(MEMBER_ID).accessToken());

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(code(response)).isEqualTo("AUTH-004");
    }

    private MockHttpServletResponse accept(Long orderId, String token) throws Exception {
        return mockMvc.perform(post("/api/admin/orders/" + orderId + "/accept")
                        .header("Authorization", "Bearer " + token))
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

    private Order order(Long martId, OrderStatus status, MartStatus martStatus) {
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
        return order;
    }
}
