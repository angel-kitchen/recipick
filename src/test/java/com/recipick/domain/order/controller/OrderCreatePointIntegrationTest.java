package com.recipick.domain.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.order.dto.request.OrderCreateRequest;
import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.order.entity.Order;
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
import com.recipick.global.security.JwtTokenProvider;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

// 주문 생성 API가 포인트를 실제 MySQL 위에서 예약·적립 예정으로 연결하는지, 실패하면 주문·포인트·재고가 함께 롤백되는지 본다.
// 롤백을 확인해야 해서 테스트 트랜잭션으로 감싸지 않고 만든 데이터를 직접 지운다.
// 상품은 2,500원 × 2개 = 상품금액 5,000원으로 맞춰 배송비 3,000원이 붙는 주문을 기본으로 쓴다
class OrderCreatePointIntegrationTest extends IntegrationTestSupport {

    // data.sql 시연 데이터: 1번 마트는 삼청동(1111014000)
    private static final Long MART_ID = 1L;
    private static final String MART_LEGAL_DONG_CODE = "1111014000";
    private static final long UNIT_PRICE = 2_500L;
    private static final int ORDER_QUANTITY = 2;
    private static final int STOCK = 10;
    private static final long HELD_POINT = 10_000L;
    // 보유 포인트 적립 건의 주문 ID는 실제 주문과 겹치지 않게 크게 띄운다(point_earning.order_id UNIQUE)
    private static final long LOT_ORDER_OFFSET = 9_500_000_000L;

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
    private MemberPointRepository memberPointRepository;
    @Autowired
    private PointEarningRepository pointEarningRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Clock clock;

    private Member member;
    private String token;
    private final List<Product> products = new ArrayList<>();
    private final List<CartItem> cartItems = new ArrayList<>();
    // 실패한 주문이 결제를 남기지 않았는지 보려고 시작 시점의 결제 건수를 기억한다
    private long paymentCountBefore;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.builder()
            .email(UUID.randomUUID() + "@op.test")
            .name("포인트 주문 회원")
            .phone("010" + String.format("%08d", ThreadLocalRandom.current().nextInt(100000000)))
            .passwordHash("hash")
            .martId(MART_ID)
            .build());
        token = tokens.createMemberTokens(member.getId()).accessToken();
        paymentCountBefore = paymentRepository.count();
    }

    // 포인트 행을 먼저 외래키 역순(이력 → 사용 → 적립 → 회원 포인트)으로 지운 뒤 주문·결제를 지운다
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
        memberRepository.delete(member);
    }

    @Test
    @DisplayName("3,000P를 쓰면 결제금액은 상품 5,000 + 배송비 3,000 − 3,000 = 5,000원이고 사용 예약과 10P 적립 예정을 만든다")
    void createOrderWithPoint() throws Exception {
        // given
        givenHeldPoint();
        Product product = product(UNIT_PRICE, STOCK);
        CartItem cartItem = cartItem(product, ORDER_QUANTITY);

        // when
        MockHttpServletResponse response = createOrder("key-1", request(cartItem, 3_000L));

        // then — 응답과 주문에 사용 포인트가 남고 적립률이 고정된다
        assertThat(response.getStatus()).isEqualTo(201);
        JsonNode data = data(response);
        assertThat(data.path("productAmount").asLong()).isEqualTo(5_000L);
        assertThat(data.path("deliveryFee").asLong()).isEqualTo(3_000L);
        assertThat(data.path("usedPoint").asLong()).isEqualTo(3_000L);
        assertThat(data.path("amount").asLong()).isEqualTo(5_000L);
        Order order = orderRepository.findById(data.path("orderId").asLong()).orElseThrow();
        assertThat(order.getUsedPoint()).isEqualTo(3_000L);
        assertThat(order.getPointRate()).isEqualByComparingTo("0.0050");
        assertThat(order.getPayAmount()).isEqualTo(5_000L);
        // 결제는 남은 금액만 카드로 받는다
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(payment.getAmount()).isEqualTo(5_000L);
        // 포인트는 사용 예약되고 (5,000 − 3,000) × 0.5% = 10P가 적립 예정이 된다
        assertThat(usageSum(order.getId(), "RESERVED")).isEqualTo(3_000L);
        assertThat(balance()).isEqualTo(HELD_POINT - 3_000L);
        assertThat(lotRemaining()).isEqualTo(HELD_POINT - 3_000L);
        PointEarning earning = pointEarningRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(earning.getStatus()).isEqualTo(PointEarningStatus.PENDING);
        assertThat(earning.getAmount()).isEqualTo(10L);
        assertThat(historyCount(PointHistoryType.USE_RESERVED)).isEqualTo(1);
        assertThat(historyCount(PointHistoryType.EARN_PENDING)).isEqualTo(1);
        assertThat(stockOf(product)).isEqualTo(STOCK - ORDER_QUANTITY);
    }

    @Test
    @DisplayName("상품금액 전액을 포인트로 내도 배송비 3,000원은 포인트로 낼 수 없어 카드 결제 3,000원이 남는다")
    void createOrderWithFullProductPoint() throws Exception {
        // given
        givenHeldPoint();
        CartItem cartItem = cartItem(product(UNIT_PRICE, STOCK), ORDER_QUANTITY);

        // when
        MockHttpServletResponse response = createOrder("key-1", request(cartItem, 5_000L));

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        JsonNode data = data(response);
        assertThat(data.path("usedPoint").asLong()).isEqualTo(5_000L);
        assertThat(data.path("amount").asLong()).isEqualTo(3_000L);
        Payment payment = paymentRepository.findByOrderId(data.path("orderId").asLong()).orElseThrow();
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(payment.getAmount()).isEqualTo(3_000L);
        // 적립 기준 금액이 0원이라 적립 예정 건은 0원으로 만들고 이력은 남기지 않는다
        PointEarning earning = pointEarningRepository.findByOrderId(data.path("orderId").asLong()).orElseThrow();
        assertThat(earning.getAmount()).isZero();
        assertThat(historyCount(PointHistoryType.EARN_PENDING)).isZero();
    }

    @Test
    @DisplayName("요청에 usedPoint가 없으면 0P 주문이고 포인트를 잠그지 않은 채 25P 적립 예정만 만든다")
    void createOrderWithoutPoint() throws Exception {
        // given — 포인트 도입 전 클라이언트처럼 usedPoint 필드를 아예 보내지 않는다
        Product product = product(UNIT_PRICE, STOCK);
        CartItem cartItem = cartItem(product, ORDER_QUANTITY);
        String body = "{\"cartItemIds\":[" + cartItem.getId() + "],\"receiverName\":\"홍길동\","
            + "\"receiverPhone\":\"01012345678\",\"address\":\"서울 종로구 삼청로 1\",\"detailAddress\":\"101호\","
            + "\"legalDongCode\":\"" + MART_LEGAL_DONG_CODE + "\"}";

        // when
        MockHttpServletResponse response = createOrderWithBody("key-1", body);

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        JsonNode data = data(response);
        assertThat(data.path("usedPoint").asLong()).isZero();
        assertThat(data.path("amount").asLong()).isEqualTo(8_000L);
        Order order = orderRepository.findById(data.path("orderId").asLong()).orElseThrow();
        assertThat(order.getUsedPoint()).isZero();
        assertThat(order.getPointRate()).isEqualByComparingTo("0.0050");
        // 사용 예약이 없고 회원 포인트 행도 만들지 않으며, 5,000 × 0.5% = 25P가 적립 예정이 된다
        assertThat(usageCount()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member_point WHERE member_id = ?",
            Integer.class, member.getId())).isZero();
        PointEarning earning = pointEarningRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(earning.getStatus()).isEqualTo(PointEarningStatus.PENDING);
        assertThat(earning.getAmount()).isEqualTo(25L);
        assertThat(historyCount(PointHistoryType.EARN_PENDING)).isEqualTo(1);
    }

    @Test
    @DisplayName("적립액이 0원인 주문도 주문당 적립 예정 건 하나는 만들고 0원 이력은 남기지 않는다")
    void createOrderWithZeroEarning() throws Exception {
        // given — 100원 × 1개면 100 × 0.5% = 0.5P라 버림으로 0P다
        CartItem cartItem = cartItem(product(100L, STOCK), 1);

        // when
        MockHttpServletResponse response = createOrder("key-1", request(cartItem, 0L));

        // then
        assertThat(response.getStatus()).isEqualTo(201);
        PointEarning earning = pointEarningRepository.findByOrderId(data(response).path("orderId").asLong())
            .orElseThrow();
        assertThat(earning.getStatus()).isEqualTo(PointEarningStatus.PENDING);
        assertThat(earning.getAmount()).isZero();
        assertThat(historyCount(PointHistoryType.EARN_PENDING)).isZero();
    }

    @Test
    @DisplayName("보유 포인트보다 많이 쓰면 POINT-001로 거절하고 재고를 잠그기 전에 끝나 주문·결제·포인트·재고가 모두 그대로다")
    void rollbackWhenPointInsufficient() throws Exception {
        // given — 1,000P만 가진 회원이 3,000P를 쓰려 한다
        givenHeldPoint(1_000L);
        Product product = product(UNIT_PRICE, STOCK);
        CartItem cartItem = cartItem(product, ORDER_QUANTITY);

        // when
        MockHttpServletResponse response = createOrder("key-1", request(cartItem, 3_000L));

        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(code(response)).isEqualTo("POINT-001");
        assertNothingCreated();
        assertThat(balance()).isEqualTo(1_000L);
        assertThat(lotRemaining()).isEqualTo(1_000L);
        assertThat(stockOf(product)).isEqualTo(STOCK);
    }

    @Test
    @DisplayName("포인트 예약 뒤 재고가 부족하면 STOCK-001로 거절하고 예약한 포인트까지 함께 롤백한다")
    void rollbackReservedPointWhenStockInsufficient() throws Exception {
        // given — 재고 1개인 상품을 2개 주문한다
        givenHeldPoint();
        Product product = product(UNIT_PRICE, 1);
        CartItem cartItem = cartItem(product, ORDER_QUANTITY);

        // when
        MockHttpServletResponse response = createOrder("key-1", request(cartItem, 3_000L));

        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(code(response)).isEqualTo("STOCK-001");
        assertNothingCreated();
        assertThat(balance()).isEqualTo(HELD_POINT);
        assertThat(lotRemaining()).isEqualTo(HELD_POINT);
        assertThat(stockOf(product)).isEqualTo(1);
    }

    @Test
    @DisplayName("포인트 예약·재고 차감 뒤 사용 포인트가 상품금액을 넘으면 ORDER-019로 거절하고 주문·포인트·재고를 모두 롤백한다")
    void rollbackEverythingWhenUsedPointExceedsProductAmount() throws Exception {
        // given — 상품금액 5,000원 주문에 6,000P를 쓰려 한다. 보유 포인트는 충분해 예약까지는 성공한다
        givenHeldPoint();
        Product product = product(UNIT_PRICE, STOCK);
        CartItem cartItem = cartItem(product, ORDER_QUANTITY);

        // when
        MockHttpServletResponse response = createOrder("key-1", request(cartItem, 6_000L));

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(code(response)).isEqualTo("ORDER-019");
        assertNothingCreated();
        assertThat(balance()).isEqualTo(HELD_POINT);
        assertThat(lotRemaining()).isEqualTo(HELD_POINT);
        assertThat(stockOf(product)).isEqualTo(STOCK);
    }

    @Test
    @DisplayName("같은 키로 같은 사용 포인트를 다시 보내면 기존 주문을 200으로 돌려주고 포인트를 다시 예약·적립하지 않는다")
    void sameKeyRetryDoesNotReservePointAgain() throws Exception {
        // given
        givenHeldPoint();
        Product product = product(UNIT_PRICE, STOCK);
        CartItem cartItem = cartItem(product, ORDER_QUANTITY);
        MockHttpServletResponse first = createOrder("key-1", request(cartItem, 3_000L));

        // when
        MockHttpServletResponse retry = createOrder("key-1", request(cartItem, 3_000L));

        // then
        assertThat(retry.getStatus()).isEqualTo(200);
        assertThat(data(retry).path("orderId").asLong()).isEqualTo(data(first).path("orderId").asLong());
        assertThat(data(retry).path("usedPoint").asLong()).isEqualTo(3_000L);
        assertThat(orderRepository.findIdsByMemberId(member.getId())).hasSize(1);
        assertThat(usageCount()).isEqualTo(1);
        assertThat(balance()).isEqualTo(HELD_POINT - 3_000L);
        assertThat(historyCount(PointHistoryType.USE_RESERVED)).isEqualTo(1);
        assertThat(historyCount(PointHistoryType.EARN_PENDING)).isEqualTo(1);
        assertThat(stockOf(product)).isEqualTo(STOCK - ORDER_QUANTITY);
    }

    @Test
    @DisplayName("같은 키로 사용 포인트만 바꿔 보내면 다른 결제 요청이라 COMMON-011로 거절하고 포인트를 더 쓰지 않는다")
    void sameKeyDifferentUsedPoint() throws Exception {
        // given
        givenHeldPoint();
        CartItem cartItem = cartItem(product(UNIT_PRICE, STOCK), ORDER_QUANTITY);
        createOrder("key-1", request(cartItem, 3_000L));

        // when
        MockHttpServletResponse response = createOrder("key-1", request(cartItem, 4_000L));

        // then
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(code(response)).isEqualTo("COMMON-011");
        assertThat(balance()).isEqualTo(HELD_POINT - 3_000L);
        assertThat(usageCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("음수 사용 포인트는 요청 검증에서 COMMON-001로 거절하고 주문을 만들지 않는다")
    void rejectNegativeUsedPoint() throws Exception {
        // given
        Product product = product(UNIT_PRICE, STOCK);
        CartItem cartItem = cartItem(product, ORDER_QUANTITY);

        // when
        MockHttpServletResponse response = createOrder("key-1", request(cartItem, -1L));

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(code(response)).isEqualTo("COMMON-001");
        assertNothingCreated();
        assertThat(stockOf(product)).isEqualTo(STOCK);
    }

    // 1년 뒤 만료되는 확정 적립 건 하나로 포인트를 가진 회원을 만든다
    private void givenHeldPoint() {
        givenHeldPoint(HELD_POINT);
    }

    private void givenHeldPoint(long amount) {
        PointEarning lot = PointEarning.pending(member.getId(), LOT_ORDER_OFFSET + member.getId(), amount);
        lot.complete(LocalDateTime.now(clock).minusDays(1));
        pointEarningRepository.save(lot);
        memberPointRepository.save(MemberPoint.builder().memberId(member.getId()).balance(amount).build());
    }

    // 실패한 주문은 주문·결제·사용 예약·적립 예정·포인트 이력을 하나도 남기지 않아야 한다
    private void assertNothingCreated() {
        assertThat(orderRepository.findIdsByMemberId(member.getId())).isEmpty();
        assertThat(paymentRepository.count()).isEqualTo(paymentCountBefore);
        assertThat(usageCount()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM point_earning WHERE member_id = ? "
            + "AND status = 'PENDING'", Integer.class, member.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM point_history WHERE member_id = ?",
            Integer.class, member.getId())).isZero();
    }

    private OrderCreateRequest request(CartItem cartItem, long usedPoint) {
        return new OrderCreateRequest(List.of(cartItem.getId()), "홍길동", "01012345678", "서울 종로구 삼청로 1",
            "101호", MART_LEGAL_DONG_CODE, usedPoint);
    }

    private MockHttpServletResponse createOrder(String idempotencyKey, OrderCreateRequest request) throws Exception {
        return createOrderWithBody(idempotencyKey, mapper.writeValueAsString(request));
    }

    private MockHttpServletResponse createOrderWithBody(String idempotencyKey, String body) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
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

    private int stockOf(Product product) {
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

    private int usageCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM point_usage pu "
                + "JOIN point_earning pe ON pu.point_earning_id = pe.id WHERE pe.member_id = ?",
            Integer.class, member.getId());
    }

    private long usageSum(Long orderId, String status) {
        return jdbcTemplate.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM point_usage "
            + "WHERE order_id = ? AND status = ?", Long.class, orderId, status);
    }

    private int historyCount(PointHistoryType type) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM point_history WHERE member_id = ? AND type = ?",
            Integer.class, member.getId(), type.name());
    }

    private JsonNode data(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString()).path("data");
    }

    private String code(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString()).path("code").asText();
    }
}
