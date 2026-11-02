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
import com.recipick.domain.order.entity.OrderStatusHistory;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.entity.enums.OrderStatusActor;
import com.recipick.domain.order.entity.enums.OrderStatusType;
import com.recipick.domain.order.repository.OrderItemRepository;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
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
import org.springframework.transaction.annotation.Transactional;

class OrderAdminQueryIntegrationTest extends IntegrationTestSupport {

    private static final Long SUPER_ADMIN_ID = 1L;
    private static final Long MART_ADMIN_ID = 2L;
    @Autowired private com.recipick.domain.product.repository.MartRepository martRepository;
    @Autowired private com.recipick.domain.member.repository.AdminRepository admins;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final java.util.List<Long> adminIds = new java.util.ArrayList<>();
    private final List<Long> createdAuditIds = new ArrayList<>();
    private static final Long MEMBER_ID = 7L;

    @Autowired
    private MemberRepository members;
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
    @Autowired
    private OrderStatusHistoryRepository historyRepository;

    // 마트별 목록을 정확히 비교해야 해서 다른 테스트가 남긴 주문과 섞이지 않도록 마트 ID를 실행마다 새로 뽑는다.
    // 감사 로그의 마트 FK도 검증하도록 실제 테스트 마트를 함께 만든다
    private Long martA;
    private Long martB;
    private final List<Order> orders = new ArrayList<>();
    private final List<OrderItem> orderItems = new ArrayList<>();
    private final List<OrderStatusHistory> histories = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Long region = ThreadLocalRandom.current().nextLong(1_000_000L, 2_000_000L);
        martA = saveMart(region);
        martB = saveMart(region + 1);
    }

    @AfterEach
    void cleanUp() {
        for (Long auditId : createdAuditIds) {
            jdbc.update("DELETE FROM admin_audit_log WHERE id = ?", auditId);
        }
        jdbc.update("DELETE FROM admin_audit_log WHERE mart_id IN (?, ?)", martA, martB);
        for (Long adminId : adminIds) {
            jdbc.update("DELETE FROM admin_audit_log WHERE admin_id = ?", adminId);
            admins.deleteById(adminId);
        }
        historyRepository.deleteAll(histories);
        orderItemRepository.deleteAll(orderItems);
        orderRepository.deleteAll(orders);
        martRepository.deleteAllById(java.util.List.of(martA, martB));
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
    @Transactional
    @DisplayName("회원 토큰으로 호출하면 403 AUTH-004로 거절한다")
    void searchOrders_memberToken() throws Exception {
        // given
        String unique = UUID.randomUUID().toString().replace("-", "");
        Member activeMember = members.saveAndFlush(Member.builder().email(unique + "@role.test")
                .name("권한 검증 회원").phone(unique.substring(0, 20)).passwordHash("hash").build());

        // when
        MockHttpServletResponse response = perform("/api/admin/orders",
                tokens.createMemberTokens(activeMember.getId()).accessToken());

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

    @Test
    @DisplayName("SUPER_ADMIN은 주문 상세를 회원·마트 ID, 상품, 배송지, 상태 변경 이력(오래된 순)과 함께 돌려준다")
    void getOrder_superAdmin() throws Exception {
        // given
        Order order = order(martA, OrderStatus.PAID, MartStatus.PREPARING, "애호박", "두부");
        history(order, OrderStatusType.ORDER, "PENDING_PAYMENT", "PAID", OrderStatusActor.SYSTEM, null, null);
        history(order, OrderStatusType.MART, "NONE", "RECEIVED", OrderStatusActor.SYSTEM, null, null);
        history(order, OrderStatusType.MART, "RECEIVED", "PREPARING", OrderStatusActor.MART, MART_ADMIN_ID, "수락");

        // when
        MockHttpServletResponse response = perform("/api/admin/orders/" + order.getId(), superToken());

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = data(response);
        assertThat(data.path("orderId").asLong()).isEqualTo(order.getId());
        assertThat(data.path("orderNumber").asText()).isEqualTo(order.getOrderNumber());
        assertThat(data.path("createdAt").asText()).endsWith("+09:00");
        assertThat(data.path("status").asText()).isEqualTo("PAID");
        assertThat(data.path("martStatus").asText()).isEqualTo("PREPARING");
        assertThat(data.path("memberId").asLong()).isEqualTo(MEMBER_ID);
        assertThat(data.path("martId").asLong()).isEqualTo(martA);
        assertThat(data.path("orderName").asText()).isEqualTo("애호박 외 1건");
        assertThat(data.path("items").findValuesAsText("productName")).containsExactly("애호박", "두부");
        JsonNode item = data.path("items").get(0);
        assertThat(item.path("unitPrice").asLong()).isEqualTo(2500L);
        assertThat(item.path("saleUnitAmount").asText()).isEqualTo("500.00");
        assertThat(item.path("quantity").asInt()).isEqualTo(2);
        assertThat(item.path("lineAmount").asLong()).isEqualTo(5000L);
        assertThat(data.path("productAmount").asLong()).isEqualTo(5000L);
        assertThat(data.path("deliveryFee").asLong()).isEqualTo(3000L);
        assertThat(data.path("usedPoint").asLong()).isZero();
        assertThat(data.path("payAmount").asLong()).isEqualTo(8000L);
        assertThat(data.path("receiverName").asText()).isEqualTo("홍길동");
        assertThat(data.path("receiverPhone").asText()).isEqualTo("01012345678");
        assertThat(data.path("address").asText()).isEqualTo("서울 종로구 삼청로 1");
        assertThat(data.path("detailAddress").asText()).isEqualTo("101호");
        assertThat(data.path("cancelReasonCode").isNull()).isTrue();
        assertThat(data.has("cancelable")).isFalse();
        assertThat(data.has("portonePaymentId")).isFalse();
        JsonNode histories = data.path("histories");
        assertThat(histories).hasSize(3);
        assertThat(histories.findValuesAsText("toStatus")).containsExactly("PAID", "RECEIVED", "PREPARING");
        JsonNode accepted = histories.get(2);
        assertThat(accepted.path("statusType").asText()).isEqualTo("MART");
        assertThat(accepted.path("fromStatus").asText()).isEqualTo("RECEIVED");
        assertThat(accepted.path("actor").asText()).isEqualTo("MART");
        assertThat(accepted.path("changedByAdminId").asLong()).isEqualTo(MART_ADMIN_ID);
        assertThat(accepted.path("reason").asText()).isEqualTo("수락");
        assertThat(accepted.path("createdAt").asText()).endsWith("+09:00");
        assertThat(histories.get(0).path("changedByAdminId").isNull()).isTrue();
    }

    @Test
    @DisplayName("MART_ADMIN은 본인 마트 주문 상세를 조회할 수 있다")
    void getOrder_martAdmin() throws Exception {
        // given
        Order order = order(martA, OrderStatus.PAID, MartStatus.RECEIVED, "애호박");

        // when
        MockHttpServletResponse response = perform("/api/admin/orders/" + order.getId(), martAdminToken(martA));

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("martId").asLong()).isEqualTo(martA);
        assertThat(data(response).path("histories")).isEmpty();
    }

    @Test
    @DisplayName("MART_ADMIN이 다른 마트 주문 상세를 조회하면 403 AUTH-005로 거절한다")
    void getOrder_martAdminOtherMart() throws Exception {
        // given
        Order others = order(martB, OrderStatus.PAID, MartStatus.RECEIVED, "양파");

        // when
        MockHttpServletResponse response = perform("/api/admin/orders/" + others.getId(), martAdminToken(martA));

        // then
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(code(response)).isEqualTo("AUTH-005");
    }

    @Test
    @DisplayName("결제 전 주문 상세는 없는 주문과 같이 ORDER-012로 응답한다")
    void getOrder_pendingOrder() throws Exception {
        // given
        Order pending = order(martA, OrderStatus.PENDING_PAYMENT, MartStatus.NONE, "대파");

        // when
        MockHttpServletResponse response = perform("/api/admin/orders/" + pending.getId(), superToken());

        // then
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(code(response)).isEqualTo("ORDER-012");
    }

    @Test
    @DisplayName("없는 주문 상세는 ORDER-012로 응답한다")
    void getOrder_notFound() throws Exception {
        // when
        MockHttpServletResponse response = perform("/api/admin/orders/" + Long.MAX_VALUE, superToken());

        // then
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(code(response)).isEqualTo("ORDER-012");
    }

    private MockHttpServletResponse perform(String url, String token) throws Exception {
        Long beforeId = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM admin_audit_log", Long.class);
        try {
            return mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                    .andReturn().getResponse();
        } finally {
            createdAuditIds.addAll(jdbc.queryForList("SELECT id FROM admin_audit_log WHERE id > ? AND admin_id = ?",
                    Long.class, beforeId, SUPER_ADMIN_ID));
        }
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
        Long adminId = admins.saveAndFlush(com.recipick.domain.member.entity.Admin.builder()
                .loginId(UUID.randomUUID().toString()).passwordHash("hash")
                .role(com.recipick.domain.member.entity.enums.AdminRole.MART_ADMIN).martId(martId).active(true).build()).getId();
        adminIds.add(adminId);
        return tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, adminId, "MART_ADMIN", martId))
                .accessToken();
    }

    private void history(Order order, OrderStatusType statusType, String from, String to,
            OrderStatusActor actor, Long changedByAdminId, String reason) {
        histories.add(historyRepository.save(OrderStatusHistory.builder()
                .order(order)
                .statusType(statusType)
                .fromStatus(from)
                .toStatus(to)
                .actor(actor)
                .changedByAdminId(changedByAdminId)
                .reason(reason)
                .build()));
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
    private Long saveMart(Long regionId) {
        return martRepository.saveAndFlush(com.recipick.domain.product.entity.Mart.builder()
                .regionId(regionId).name("주문 감사 테스트 마트")
                .latitude(new BigDecimal("37.5")).longitude(new BigDecimal("127.0"))
                .serviceRadiusM(2000).build()).getId();
    }

    @Test
    @DisplayName("슈퍼 관리자가 실제 마트의 빈 목록을 조회해도 해당 마트를 감사 기록에 남긴다")
    void recordsExistingEmptyMartScope() throws Exception {
        // given, when
        var response = perform("/api/admin/orders?martId=" + martA, superToken());
        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(data(response).path("totalElements").asInt()).isZero();
        assertThat(createdAuditIds).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT mart_id FROM admin_audit_log WHERE id = ?", Long.class,
                createdAuditIds.get(0))).isEqualTo(martA);
    }

    @Test
    @DisplayName("없는 마트와 전체 마트 조회는 마트 ID 없이 기록하고 빈 목록 응답도 유지한다")
    void recordsUnknownAndAllMartScopes() throws Exception {
        // given, when
        var missing = perform("/api/admin/orders?martId=" + Long.MAX_VALUE, superToken());
        var all = perform("/api/admin/orders", superToken());
        // then
        assertThat(missing.getStatus()).isEqualTo(200);
        assertThat(data(missing).path("totalElements").asInt()).isZero();
        assertThat(all.getStatus()).isEqualTo(200);
        assertThat(createdAuditIds).hasSize(2);
        for (Long id : createdAuditIds) {
            assertThat(jdbc.queryForObject("SELECT mart_id FROM admin_audit_log WHERE id = ?", Long.class, id)).isNull();
        }
    }

}
