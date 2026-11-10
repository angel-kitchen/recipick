package com.recipick.domain.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.dto.MembershipConsent;
import com.recipick.domain.member.entity.Admin;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.entity.Membership;
import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.member.entity.enums.MembershipSource;
import com.recipick.domain.member.entity.enums.MembershipType;
import com.recipick.domain.member.repository.AdminRepository;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.member.repository.MembershipRepository;
import com.recipick.domain.order.dto.request.OrderCreateRequest;
import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.repository.CartItemRepository;
import com.recipick.domain.order.repository.OrderItemRepository;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
import com.recipick.domain.payment.entity.MemberPoint;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.PointEarning;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.entity.enums.PointEarningStatus;
import com.recipick.domain.payment.entity.enums.PointHistoryType;
import com.recipick.domain.payment.repository.MemberPointRepository;
import com.recipick.domain.payment.repository.PaymentRepository;
import com.recipick.domain.payment.repository.PointEarningRepository;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import java.math.BigDecimal;
import java.time.Clock;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

// 주문 생성이 기준시각 한 번의 이용권 조회로 적립률과 멤버십 배송비 혜택을 함께 정하고, 그 적립률이 주문에 남아
// 이후 이용권이 끝나도 배송완료 적립에 그대로 쓰이는지 실제 MySQL 위에서 본다.
// 롤백·확정을 확인해야 해서 테스트 트랜잭션으로 감싸지 않고 만든 데이터를 직접 지운다.
// 상품은 5,000원 × 2개 = 상품금액 10,000원에 2,000P를 써서 적립 기준 금액 8,000원(일반 40P, 유료 80P)을 기본으로 쓴다
class OrderCreateMembershipRateIntegrationTest extends IntegrationTestSupport {

    // data.sql 시연 데이터: 1번 마트는 삼청동(1111014000)
    private static final Long MART_ID = 1L;
    private static final String MART_LEGAL_DONG_CODE = "1111014000";
    private static final long UNIT_PRICE = 5_000L;
    private static final int STOCK = 10;
    private static final long HELD_POINT = 20_000L;
    // 보유 포인트 적립 건의 주문 ID는 실제 주문과 겹치지 않게 크게 띄운다(point_earning.order_id UNIQUE)
    private static final long LOT_ORDER_OFFSET = 9_600_000_000L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JwtTokenProvider tokens;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MembershipRepository membershipRepository;
    @Autowired
    private AdminRepository adminRepository;
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
    private MemberPointRepository memberPointRepository;
    @Autowired
    private PointEarningRepository pointEarningRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Clock clock;

    private Member member;
    private String token;
    private LocalDateTime now;
    private Admin admin;
    private final List<Product> products = new ArrayList<>();
    private final List<CartItem> cartItems = new ArrayList<>();

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.builder()
            .email(UUID.randomUUID() + "@mr.test")
            .name("적립률 주문 회원")
            .phone("010" + String.format("%08d", ThreadLocalRandom.current().nextInt(100000000)))
            .passwordHash("hash")
            .martId(MART_ID)
            .build());
        token = testMemberTokens(member.getId()).accessToken();
        now = LocalDateTime.now(clock);
    }

    // 포인트 행을 외래키 역순(이력 → 사용 → 적립 → 회원 포인트)으로 지운 뒤 주문·결제·이용권·관리자를 지운다
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM point_history WHERE member_id = ?", member.getId());
        jdbcTemplate.update("DELETE pu FROM point_usage pu JOIN point_earning pe ON pu.point_earning_id = pe.id "
            + "WHERE pe.member_id = ?", member.getId());
        jdbcTemplate.update("DELETE FROM point_earning WHERE member_id = ?", member.getId());
        jdbcTemplate.update("DELETE FROM member_point WHERE member_id = ?", member.getId());
        for (Long orderId : orderRepository.findIdsByMemberId(member.getId())) {
            paymentRepository.findByOrderId(orderId).ifPresent(paymentRepository::delete);
            orderStatusHistoryRepository.deleteAll(orderStatusHistoryRepository.findAllByOrderId(orderId));
            orderItemRepository.deleteAll(orderItemRepository.findAllByOrderId(orderId));
            orderRepository.deleteById(orderId);
        }
        cartItemRepository.deleteAll(cartItems);
        productRepository.deleteAll(products);
        jdbcTemplate.update("DELETE FROM membership WHERE member_id = ?", member.getId());
        if (admin != null) {
            jdbcTemplate.update("DELETE FROM admin_audit_log WHERE admin_id = ?", admin.getId());
            adminRepository.deleteById(admin.getId());
        }
        memberRepository.delete(member);
    }

    @Test
    @DisplayName("유료 회원은 적립률 1%가 주문에 저장되고 (10,000 − 2,000) × 1% = 80P가 적립 예정이 되며 배송비는 적립하지 않는다")
    void paidMembershipStoresOnePercentRate() throws Exception {
        // given
        givenHeldPoint();
        activeMembership(MembershipType.PAID, now.minusDays(1), now.plusDays(30));
        CartItem cartItem = cartItem(product(UNIT_PRICE, STOCK), 2);

        // when
        MockHttpServletResponse response = createOrder("key-1", request(cartItem, 2_000L));

        // then — 상품 10,000원은 멤버십 무료배송 기준(15,000원) 미만이라 배송비 3,000원이 붙는다
        assertThat(response.getStatus()).isEqualTo(201);
        JsonNode data = data(response);
        assertThat(data.path("deliveryFee").asLong()).isEqualTo(3_000L);
        assertThat(data.path("amount").asLong()).isEqualTo(11_000L);
        Order order = orderRepository.findById(data.path("orderId").asLong()).orElseThrow();
        assertThat(order.getPointRate()).isEqualByComparingTo("0.0100");
        PointEarning earning = pointEarningRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(earning.getStatus()).isEqualTo(PointEarningStatus.PENDING);
        assertThat(earning.getAmount()).isEqualTo(80L);
        assertThat(historyCount(PointHistoryType.EARN_PENDING)).isEqualTo(1);
    }

    @Test
    @DisplayName("무료체험 회원은 일반 회원과 같은 0.5%로 40P가 적립 예정이 된다")
    void trialMembershipKeepsStandardRate() throws Exception {
        // given
        givenHeldPoint();
        activeMembership(MembershipType.TRIAL, now.minusDays(1), now.plusDays(6));
        CartItem cartItem = cartItem(product(UNIT_PRICE, STOCK), 2);

        // when
        MockHttpServletResponse response = createOrder("key-1", request(cartItem, 2_000L));

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        Order order = orderRepository.findById(data(response).path("orderId").asLong()).orElseThrow();
        assertThat(order.getPointRate()).isEqualByComparingTo("0.0050");
        assertThat(pointEarningRepository.findByOrderId(order.getId()).orElseThrow().getAmount()).isEqualTo(40L);
    }

    @Test
    @DisplayName("이용권이 없는 일반 회원은 기존대로 0.5%로 40P가 적립 예정이 된다")
    void memberWithoutMembershipKeepsStandardRate() throws Exception {
        // given
        givenHeldPoint();
        CartItem cartItem = cartItem(product(UNIT_PRICE, STOCK), 2);

        // when
        MockHttpServletResponse response = createOrder("key-1", request(cartItem, 2_000L));

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        Order order = orderRepository.findById(data(response).path("orderId").asLong()).orElseThrow();
        assertThat(order.getPointRate()).isEqualByComparingTo("0.0050");
        assertThat(pointEarningRepository.findByOrderId(order.getId()).orElseThrow().getAmount()).isEqualTo(40L);
    }

    @Test
    @DisplayName("유료 회원은 한 번의 이용권 조회로 적립률 1%와 멤버십 무료배송을 함께 받는다")
    void paidMembershipGetsRateAndFreeDeliveryFromOneLookup() throws Exception {
        // given — 5,000원 × 3개 = 15,000원은 멤버십 무료배송 기준이다
        activeMembership(MembershipType.PAID, now.minusDays(1), now.plusDays(30));
        CartItem cartItem = cartItem(product(UNIT_PRICE, STOCK), 3);

        // when
        MockHttpServletResponse response = createOrder("key-1", request(cartItem, 0L));

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        JsonNode data = data(response);
        assertThat(data.path("deliveryFee").asLong()).isZero();
        assertThat(data.path("amount").asLong()).isEqualTo(15_000L);
        Order order = orderRepository.findById(data.path("orderId").asLong()).orElseThrow();
        assertThat(order.getPointRate()).isEqualByComparingTo("0.0100");
        assertThat(pointEarningRepository.findByOrderId(order.getId()).orElseThrow().getAmount()).isEqualTo(150L);
    }

    @Test
    @DisplayName("아직 결제되지 않은 유료 이용권은 적립률 0.5%이고 멤버십 무료배송도 받지 못한다")
    void paymentPendingPaidMembershipGetsNoBenefit() throws Exception {
        // given — 결제 대기 이용권은 activated_at이 없다
        membershipRepository.saveAndFlush(Membership.pendingPaid(member, MembershipSource.SUBSCRIBE,
            now.minusMinutes(5), now.plusMonths(1), new MembershipConsent(3_900L, "v1", now.minusMinutes(5))));
        CartItem cartItem = cartItem(product(UNIT_PRICE, STOCK), 3);

        // when
        MockHttpServletResponse response = createOrder("key-1", request(cartItem, 0L));

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        JsonNode data = data(response);
        assertThat(data.path("deliveryFee").asLong()).isEqualTo(3_000L);
        assertThat(data.path("amount").asLong()).isEqualTo(18_000L);
        Order order = orderRepository.findById(data.path("orderId").asLong()).orElseThrow();
        assertThat(order.getPointRate()).isEqualByComparingTo("0.0050");
        assertThat(pointEarningRepository.findByOrderId(order.getId()).orElseThrow().getAmount()).isEqualTo(75L);
    }

    @Test
    @DisplayName("유료 갱신 실패 뒤 유예 중인 회원은 적립률 1%를 유지한다")
    void graceMembershipKeepsOnePercent() throws Exception {
        // given — 원래 종료 시각은 지났지만 유예 종료 시각이 남아 있다
        givenHeldPoint();
        Membership membership = Membership.builder().member(member).type(MembershipType.PAID)
            .source(MembershipSource.SUBSCRIBE).startAt(now.minusDays(30)).activatedAt(now.minusDays(30))
            .endAt(now.minusDays(1)).autoRenew(true).build();
        ReflectionTestUtils.setField(membership, "graceEndAt", now.plusDays(6));
        membershipRepository.saveAndFlush(membership);
        CartItem cartItem = cartItem(product(UNIT_PRICE, STOCK), 2);

        // when
        MockHttpServletResponse response = createOrder("key-1", request(cartItem, 2_000L));

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        Order order = orderRepository.findById(data(response).path("orderId").asLong()).orElseThrow();
        assertThat(order.getPointRate()).isEqualByComparingTo("0.0100");
        assertThat(pointEarningRepository.findByOrderId(order.getId()).orElseThrow().getAmount()).isEqualTo(80L);
    }

    @Test
    @DisplayName("즉시 해지가 끝난 유료 이용권은 혜택이 없어 적립률 0.5%다")
    void terminatedMembershipKeepsStandardRate() throws Exception {
        // given
        givenHeldPoint();
        Membership membership = activeMembership(MembershipType.PAID, now.minusDays(1), now.plusDays(30));
        membership.terminateAfterRefund(now.minusHours(1));
        membershipRepository.saveAndFlush(membership);
        CartItem cartItem = cartItem(product(UNIT_PRICE, STOCK), 2);

        // when
        MockHttpServletResponse response = createOrder("key-1", request(cartItem, 2_000L));

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        Order order = orderRepository.findById(data(response).path("orderId").asLong()).orElseThrow();
        assertThat(order.getPointRate()).isEqualByComparingTo("0.0050");
        assertThat(pointEarningRepository.findByOrderId(order.getId()).orElseThrow().getAmount()).isEqualTo(40L);
    }

    @Test
    @DisplayName("주문 뒤 이용권이 끝나도 저장된 적립률과 적립 예정액은 그대로이고 배송완료 때 그 금액이 한 번만 확정된다")
    void storedRateSurvivesMembershipEndAndIsConfirmedOnDelivery() throws Exception {
        // given — 포인트를 쓰지 않은 유료 회원 주문이라 적립 예정은 10,000 × 1% = 100P다
        Membership membership = activeMembership(MembershipType.PAID, now.minusDays(1), now.plusDays(30));
        CartItem cartItem = cartItem(product(UNIT_PRICE, STOCK), 2);
        long orderId = data(createOrder("key-1", request(cartItem, 0L))).path("orderId").asLong();
        membership.terminateAfterRefund(LocalDateTime.now(clock));
        membershipRepository.saveAndFlush(membership);
        givenPaidAndShipping(orderId);
        givenMartAdmin();

        // when
        MockHttpServletResponse response = changeDeliveryStatus(orderId, "DELIVERED");

        // then — 이용권이 끝난 뒤에도 주문 당시 적립률로 만든 금액이 재계산 없이 확정된다
        assertThat(response.getStatus()).isEqualTo(200);
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getMartStatus()).isEqualTo(MartStatus.DELIVERED);
        assertThat(order.getPointRate()).isEqualByComparingTo("0.0100");
        PointEarning earning = pointEarningRepository.findByOrderId(orderId).orElseThrow();
        assertThat(earning.getStatus()).isEqualTo(PointEarningStatus.COMPLETED);
        assertThat(earning.getAmount()).isEqualTo(100L);
        assertThat(earning.getRemainingAmount()).isEqualTo(100L);
        assertThat(balance()).isEqualTo(100L);
        assertThat(historyCount(PointHistoryType.EARN_COMPLETED)).isEqualTo(1);
    }

    @Test
    @DisplayName("유료 회원이 포인트로 전액을 내면 기존대로 PG 없이 결제 완료되고 적립 기준 금액이 0원이라 0P 적립 예정이 된다")
    void paidMembershipPointsOnlyOrderCompletesWithoutPg() throws Exception {
        // given — 15,000원 상품을 멤버십 무료배송으로 15,000P에 사면 외부 결제금액은 0원이다
        givenHeldPoint();
        activeMembership(MembershipType.PAID, now.minusDays(1), now.plusDays(30));
        CartItem cartItem = cartItem(product(UNIT_PRICE, STOCK), 3);

        // when
        MockHttpServletResponse response = createOrder("key-1", request(cartItem, 15_000L));

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        JsonNode data = data(response);
        assertThat(data.path("amount").asLong()).isZero();
        assertThat(data.path("portonePaymentId").isNull()).isTrue();
        Order order = orderRepository.findById(data.path("orderId").asLong()).orElseThrow();
        assertThat(order.getPointRate()).isEqualByComparingTo("0.0100");
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.POINTS_ONLY);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(pointEarningRepository.findByOrderId(order.getId()).orElseThrow().getAmount()).isZero();
    }

    private Membership activeMembership(MembershipType type, LocalDateTime start, LocalDateTime end) {
        return membershipRepository.saveAndFlush(Membership.builder().member(member).type(type)
            .source(type == MembershipType.TRIAL ? MembershipSource.TRIAL : MembershipSource.SUBSCRIBE)
            .startAt(start).activatedAt(start).endAt(end).autoRenew(true).build());
    }

    // 1년 뒤 만료되는 확정 적립 건 하나로 포인트를 가진 회원을 만든다
    private void givenHeldPoint() {
        PointEarning lot = PointEarning.pending(member.getId(), LOT_ORDER_OFFSET + member.getId(), HELD_POINT);
        lot.complete(now.minusDays(1));
        pointEarningRepository.save(lot);
        memberPointRepository.save(MemberPoint.builder().memberId(member.getId()).balance(HELD_POINT).build());
    }

    // 결제 성공과 마트 접수·배송중까지는 주문 엔티티의 전이 규칙으로 만들고, 배송완료만 API로 보낸다
    private void givenPaidAndShipping(long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.markPaid(LocalDateTime.now(clock));
        order.acceptByMart();
        order.changeDeliveryStatus(MartStatus.SHIPPING);
        orderRepository.saveAndFlush(order);
    }

    private void givenMartAdmin() {
        admin = adminRepository.saveAndFlush(Admin.builder()
            .loginId(UUID.randomUUID().toString())
            .passwordHash("hash")
            .role(AdminRole.MART_ADMIN)
            .martId(MART_ID)
            .active(true)
            .build());
    }

    private MockHttpServletResponse changeDeliveryStatus(long orderId, String status) throws Exception {
        String adminToken = testAdminTokens(
            new AuthPrincipal(SubjectType.ADMIN, admin.getId(), "MART_ADMIN", MART_ID)).accessToken();
        MockHttpServletResponse response = mockMvc.perform(
                patch("/api/admin/orders/{orderId}/delivery-status", orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\": \"" + status + "\"}"))
            .andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        return response;
    }

    private OrderCreateRequest request(CartItem cartItem, long usedPoint) {
        return new OrderCreateRequest(List.of(cartItem.getId()), "홍길동", "01012345678", "서울 종로구 삼청로 1",
            "101호", MART_LEGAL_DONG_CODE, usedPoint);
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

    private Product product(long price, int stock) {
        Mart mart = martRepository.findById(MART_ID).orElseThrow();
        Product product = productRepository.save(Product.builder()
            .mart(mart)
            .name("애호박")
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

    // 잠금 조회는 트랜잭션이 필요해 값만 직접 읽는다
    private long balance() {
        return jdbcTemplate.queryForObject("SELECT balance FROM member_point WHERE member_id = ?",
            Long.class, member.getId());
    }

    private int historyCount(PointHistoryType type) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM point_history WHERE member_id = ? AND type = ?",
            Integer.class, member.getId(), type.name());
    }

    private JsonNode data(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString()).path("data");
    }
}
