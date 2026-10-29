package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.order.dto.request.OrderCreateRequest;
import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.repository.CartItemRepository;
import com.recipick.domain.payment.entity.MemberPoint;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.PointEarning;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.PointEarningStatus;
import com.recipick.domain.payment.entity.enums.PointHistoryType;
import com.recipick.domain.payment.repository.MemberPointRepository;
import com.recipick.domain.payment.repository.PointEarningRepository;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

// 외부 결제금액이 0원인 주문이 실제 MySQL·Redis 위에서 PG 없이 생성·회원 취소·마트 거절까지 정확히 한 번 반영되는지 본다.
// 부모 Support에서는 PortOne mock·MockMvc·Redis 큐·Clock만 쓴다. 주문·결제는 HTTP 주문 생성 API가 만들어 부모의 정리 목록에
// 들어가지 않으므로 이 테스트가 회원 기준으로 직접 지운다
class PointsOnlyPaymentIntegrationTest extends PaymentFlowIntegrationSupport {

    // data.sql 시연 데이터: 1번 마트는 삼청동(1111014000)
    private static final Long MART_ID = 1L;
    private static final String MART_LEGAL_DONG_CODE = "1111014000";
    // 20,000원 × 2개 = 상품금액 40,000원이라 무료배송이고, 40,000P를 모두 쓰면 외부 결제금액이 0원이다
    private static final long UNIT_PRICE = 20_000L;
    private static final int QUANTITY = 2;
    private static final int STOCK = 10;
    private static final long USED_POINT = 40_000L;
    private static final long HELD_POINT = 50_000L;
    // 보유 포인트 적립 건의 주문 ID는 실제 주문과 겹치지 않게 크게 띄운다(point_earning.order_id UNIQUE)
    private static final long LOT_ORDER_OFFSET = 9_600_000_000L;
    private static final String CANCEL_BODY = "{\"reason\":\"단순 변심\"}";
    private static final String REJECT_BODY = "{\"reason\":\"상품 준비가 어렵습니다.\"}";

    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private CartItemRepository cartItemRepository;
    @Autowired
    private MemberPointRepository memberPointRepository;
    @Autowired
    private PointEarningRepository pointEarningRepository;

    private Member member;
    private String memberToken;
    private Product product;
    private final List<CartItem> cartItems = new ArrayList<>();
    private final List<Long> adminIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.builder()
            .email(UUID.randomUUID() + "@po.test")
            .name("0원 결제 회원")
            .phone("010" + String.format("%08d", ThreadLocalRandom.current().nextInt(100000000)))
            .passwordHash("hash")
            .martId(MART_ID)
            .build());
        memberToken = tokenProvider.createMemberTokens(member.getId()).accessToken();
        Mart mart = martRepository.findById(MART_ID).orElseThrow();
        product = productRepository.save(Product.builder()
            .mart(mart)
            .name("한우 선물세트")
            .price(UNIT_PRICE)
            .saleUnitAmount(new BigDecimal("1"))
            .saleUnit(SaleUnit.EA)
            .totalQuantity(STOCK)
            .build());
        // 1년 뒤 만료되는 확정 적립 건 하나로 50,000P를 가진 회원을 만든다
        PointEarning lot = PointEarning.pending(member.getId(), LOT_ORDER_OFFSET + member.getId(), HELD_POINT);
        lot.complete(LocalDateTime.now(clock).minusDays(1));
        pointEarningRepository.save(lot);
        memberPointRepository.save(MemberPoint.builder().memberId(member.getId()).balance(HELD_POINT).build());
    }

    // 부모의 정리(@AfterEach)보다 먼저 돈다. 외래키 역순으로 포인트 → 환불 → 결제(남은 만료 작업 포함) → 주문 →
    // 장바구니 → 상품 → 감사 로그 → 관리자 → 회원 순으로 지운다
    @AfterEach
    void cleanUpPointsOnlyFlow() {
        Long memberId = member.getId();
        jdbcTemplate.update("DELETE FROM point_history WHERE member_id = ?", memberId);
        jdbcTemplate.update("DELETE pu FROM point_usage pu JOIN point_earning pe ON pu.point_earning_id = pe.id "
            + "WHERE pe.member_id = ?", memberId);
        jdbcTemplate.update("DELETE FROM point_earning WHERE member_id = ?", memberId);
        jdbcTemplate.update("DELETE FROM member_point WHERE member_id = ?", memberId);
        List<Long> orderIds = orderRepository.findIdsByMemberId(memberId);
        for (Long orderId : orderIds) {
            paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
                // 정상 0원 흐름에는 환불이 없지만, 버그로 카드 환불 경로를 탔다면 남은 환불 작업이 다음 테스트를 오염시키지 않게
                // 부모 Support와 같은 방식으로 환불 작업 3종과 만료 작업을 함께 지운다
                List<Refund> refunds = refundRepository.findAllByPaymentIdIn(List.of(payment.getId()));
                for (Refund refund : refunds) {
                    removeTask(PaymentDelayTask.refundReconcile(refund.getId()));
                    removeTask(PaymentDelayTask.systemRefundExecute(refund.getId()));
                    removeTask(PaymentDelayTask.adminRefundExecute(refund.getId()));
                }
                refundRepository.deleteAll(refunds);
                removeTask(PaymentDelayTask.paymentExpire(payment.getId()));
                paymentRepository.delete(payment);
            });
            orderStatusHistoryRepository.deleteAll(orderStatusHistoryRepository.findAllByOrderId(orderId));
            orderItemRepository.deleteAll(orderItemRepository.findAllByOrderId(orderId));
            orderRepository.deleteById(orderId);
        }
        cartItemRepository.deleteAll(cartItems);
        productRepository.delete(product);
        for (Long adminId : adminIds) {
            jdbcTemplate.update("DELETE FROM admin_audit_log WHERE admin_id = ?", adminId);
        }
        adminRepository.deleteAllById(adminIds);
        memberRepository.delete(member);
    }

    @Test
    @DisplayName("상품 40,000원을 40,000P로 사면 PG 없이 주문 생성 트랜잭션에서 결제·주문·포인트 사용까지 완료되고 만료 작업이 없다")
    void createPointsOnlyOrder() throws Exception {
        // when
        MockHttpServletResponse response = createOrder();

        // then — 응답은 결제 완료 주문이고 PortOne 결제창에 필요한 값이 없다
        assertThat(response.getStatus()).isEqualTo(201);
        JsonNode data = data(response);
        assertThat(data.path("status").asText()).isEqualTo("PAID");
        assertThat(data.path("productAmount").asLong()).isEqualTo(40_000L);
        assertThat(data.path("deliveryFee").asLong()).isZero();
        assertThat(data.path("usedPoint").asLong()).isEqualTo(USED_POINT);
        assertThat(data.path("amount").asLong()).isZero();
        assertThat(data.path("portonePaymentId").isNull()).isTrue();
        assertThat(data.path("expiresAt").isNull()).isTrue();

        Order order = orderRepository.findById(data.path("orderId").asLong()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.RECEIVED);
        assertThat(order.getPendingMemberId()).isNull();
        assertThat(order.getPayAmount()).isZero();
        assertThat(order.getPaidAt()).isNotNull();

        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.POINTS_ONLY);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getAmount()).isZero();
        assertThat(payment.getPortonePaymentId()).isNull();
        assertThat(payment.getExpiresAt()).isNull();
        assertThat(payment.getPaidAt()).isEqualTo(order.getPaidAt());
        assertThat(refundRepository.findAllByPaymentIdIn(List.of(payment.getId()))).isEmpty();
        // 생성 이벤트를 발행하지 않았으니 5분 만료 작업도 예약되지 않았다
        assertThat(scoreOf(PaymentDelayTask.paymentExpire(payment.getId()))).isNull();

        // 사용 포인트는 바로 확정되고, 적립 기준 금액이 0원이라 적립 예정은 0P다
        assertThat(usageStatus(order.getId())).isEqualTo("CONFIRMED");
        assertThat(balance()).isEqualTo(HELD_POINT - USED_POINT);
        assertThat(lotRemaining()).isEqualTo(HELD_POINT - USED_POINT);
        PointEarning earning = pointEarningRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(earning.getStatus()).isEqualTo(PointEarningStatus.PENDING);
        assertThat(earning.getAmount()).isZero();
        assertThat(historyCount(PointHistoryType.USE_RESERVED)).isEqualTo(1);
        assertThat(historyCount(PointHistoryType.USE_CONFIRMED)).isEqualTo(1);
        assertThat(historyCount(PointHistoryType.EARN_PENDING)).isZero();
        assertThat(stock()).isEqualTo(STOCK - QUANTITY);
        verifyNoInteractions(portOnePaymentClient);
    }

    @Test
    @DisplayName("0원 결제 주문을 회원이 취소하면 PG·환불 행 없이 결제·주문이 닫히고 포인트·재고가 돌아오며, 다시 요청해도 한 번만 반영된다")
    void memberCancelsPointsOnlyOrder() throws Exception {
        // given
        JsonNode created = data(createOrder());
        Long orderId = created.path("orderId").asLong();
        Long paymentId = created.path("paymentId").asLong();

        // when
        MockHttpServletResponse response = cancel(paymentId, "cancel-key-1");

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = data(response);
        assertThat(data.path("paymentStatus").asText()).isEqualTo("CANCELED");
        assertThat(data.path("refundStatus").isNull()).isTrue();
        assertThat(data.path("refundAmount").asLong()).isZero();
        assertCanceledAndRestored(orderId, paymentId, MartStatus.RECEIVED);

        // when — 같은 키 재요청과 다른 키 재요청
        MockHttpServletResponse sameKey = cancel(paymentId, "cancel-key-1");
        MockHttpServletResponse otherKey = cancel(paymentId, "cancel-key-2");

        // then — 끝난 취소의 결과만 돌려주고 자산은 다시 움직이지 않는다
        assertThat(sameKey.getStatus()).isEqualTo(200);
        assertThat(otherKey.getStatus()).isEqualTo(200);
        assertThat(data(otherKey).path("paymentStatus").asText()).isEqualTo("CANCELED");
        assertCanceledAndRestored(orderId, paymentId, MartStatus.RECEIVED);
        verifyNoInteractions(portOnePaymentClient);
    }

    @Test
    @DisplayName("0원 결제 주문을 마트가 거절하면 PG·ADMIN 환불 없이 결제·주문이 닫히고 포인트·재고가 돌아오며 감사 로그는 재요청에도 한 건이다")
    void martRejectsPointsOnlyOrder() throws Exception {
        // given
        JsonNode created = data(createOrder());
        Long orderId = created.path("orderId").asLong();
        Long paymentId = created.path("paymentId").asLong();
        Admin admin = saveMartAdmin();

        // when
        MockHttpServletResponse response = reject(orderId, admin, "reject-key-1");

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode data = data(response);
        assertThat(data.path("paymentStatus").asText()).isEqualTo("CANCELED");
        assertThat(data.path("refundId").isNull()).isTrue();
        assertThat(data.path("refundAmount").asLong()).isZero();
        assertCanceledAndRestored(orderId, paymentId, MartStatus.REJECTED);
        assertThat(auditCount(admin)).isEqualTo(1);

        // when — 같은 키 재요청
        MockHttpServletResponse replay = reject(orderId, admin, "reject-key-1");

        // then — 이미 거절된 주문이라 자산·감사 로그를 다시 남기지 않는다
        assertThat(replay.getStatus()).isEqualTo(200);
        assertCanceledAndRestored(orderId, paymentId, MartStatus.REJECTED);
        assertThat(auditCount(admin)).isEqualTo(1);
        verifyNoInteractions(portOnePaymentClient);
    }

    // 취소·거절 뒤에는 결제 CANCELED, 주문 REFUNDED, 사용 포인트 RESTORED, 잔액·적립 건·재고 원복, 적립 예정 CANCELED,
    // 환불 0건이어야 하고, 몇 번을 다시 불러도 복원 이력은 한 건이다
    private void assertCanceledAndRestored(Long orderId, Long paymentId, MartStatus expectedMartStatus) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCanceledAt()).isNotNull();
        assertThat(refundRepository.findAllByPaymentIdIn(List.of(paymentId))).isEmpty();
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getMartStatus()).isEqualTo(expectedMartStatus);
        assertThat(usageStatus(orderId)).isEqualTo("RESTORED");
        assertThat(balance()).isEqualTo(HELD_POINT);
        assertThat(lotRemaining()).isEqualTo(HELD_POINT);
        assertThat(pointEarningRepository.findByOrderId(orderId).orElseThrow().getStatus())
            .isEqualTo(PointEarningStatus.CANCELED);
        assertThat(historyCount(PointHistoryType.RESTORED)).isEqualTo(1);
        assertThat(stock()).isEqualTo(STOCK);
    }

    private MockHttpServletResponse createOrder() throws Exception {
        CartItem cartItem = cartItemRepository.save(CartItem.builder()
            .memberId(member.getId())
            .productId(product.getId())
            .quantity(QUANTITY)
            .build());
        cartItems.add(cartItem);
        OrderCreateRequest request = new OrderCreateRequest(List.of(cartItem.getId()), "홍길동", "01012345678",
            "서울 종로구 삼청로 1", "101호", MART_LEGAL_DONG_CODE, USED_POINT);
        MockHttpServletResponse response = mockMvc.perform(post("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                .header("Idempotency-Key", "points-only-order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)))
            .andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        return response;
    }

    private MockHttpServletResponse cancel(Long paymentId, String idempotencyKey) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/payments/{paymentId}/cancel", paymentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CANCEL_BODY))
            .andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        return response;
    }

    private MockHttpServletResponse reject(Long orderId, Admin admin, String idempotencyKey) throws Exception {
        String token = tokenProvider.createTokens(new AuthPrincipal(SubjectType.ADMIN, admin.getId(),
            admin.getRole().name(), admin.getMartId())).accessToken();
        MockHttpServletResponse response = mockMvc.perform(post("/api/admin/orders/{orderId}/reject", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(REJECT_BODY))
            .andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        return response;
    }

    // 감사 기록이 실제 관리자 행과 토큰을 대조하므로 1번 마트의 마트 관리자를 실제로 만든다
    private Admin saveMartAdmin() {
        Admin admin = adminRepository.saveAndFlush(Admin.builder()
            .loginId(UUID.randomUUID().toString())
            .passwordHash("hash")
            .role(AdminRole.MART_ADMIN)
            .martId(MART_ID)
            .active(true)
            .build());
        adminIds.add(admin.getId());
        return admin;
    }

    private int stock() {
        return productRepository.findById(product.getId()).orElseThrow().getTotalQuantity();
    }

    // 잠금 조회는 트랜잭션이 필요해 값만 직접 읽는다
    private long balance() {
        return jdbcTemplate.queryForObject("SELECT balance FROM member_point WHERE member_id = ?",
            Long.class, member.getId());
    }

    private long lotRemaining() {
        return jdbcTemplate.queryForObject("SELECT remaining_amount FROM point_earning WHERE order_id = ?",
            Long.class, LOT_ORDER_OFFSET + member.getId());
    }

    private String usageStatus(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM point_usage WHERE order_id = ?", String.class, orderId);
    }

    private int historyCount(PointHistoryType type) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM point_history WHERE member_id = ? AND type = ?",
            Integer.class, member.getId(), type.name());
    }

    private int auditCount(Admin admin) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE admin_id = ?",
            Integer.class, admin.getId());
    }

    private JsonNode data(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString()).path("data");
    }
}
