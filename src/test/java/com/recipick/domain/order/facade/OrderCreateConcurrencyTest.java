package com.recipick.domain.order.facade;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.order.dto.OrderCreateResult;
import com.recipick.domain.order.dto.request.OrderCreateRequest;
import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.order.repository.CartItemRepository;
import com.recipick.domain.order.repository.OrderItemRepository;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
import com.recipick.domain.payment.entity.MemberPoint;
import com.recipick.domain.payment.entity.PointEarning;
import com.recipick.domain.payment.repository.MemberPointRepository;
import com.recipick.domain.payment.repository.PaymentRepository;
import com.recipick.domain.payment.repository.PointEarningRepository;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class OrderCreateConcurrencyTest extends IntegrationTestSupport {

    // data.sql 시연 데이터: 1번 마트는 삼청동(1111014000)
    private static final Long MART_ID = 1L;
    private static final String LEGAL_DONG_CODE = "1111014000";
    private static final int THREADS = 5;
    // 포인트 주문 테스트의 적립 건 주문 ID. 주문 행과 외래키로 묶이지 않아 실제 주문이 아니어도 된다
    private static final Long EARLY_LOT_ORDER_ID = 9_600_101L;
    private static final Long LATE_LOT_ORDER_ID = 9_600_102L;

    @Autowired
    private OrderFacade orderFacade;
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
    private PointEarningRepository pointEarningRepository;
    @Autowired
    private MemberPointRepository memberPointRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Clock clock;

    private final List<Member> members = new ArrayList<>();
    private final List<CartItem> cartItems = new ArrayList<>();
    private Product product;

    @AfterEach
    void cleanUp() {
        for (Member member : members) {
            deletePoints(member.getId());
            for (Long orderId : orderRepository.findIdsByMemberId(member.getId())) {
                paymentRepository.findByOrderId(orderId).ifPresent(paymentRepository::delete);
                orderStatusHistoryRepository.deleteAll(orderStatusHistoryRepository.findAllByOrderId(orderId));
                orderItemRepository.deleteAll(orderItemRepository.findAllByOrderId(orderId));
                orderRepository.deleteById(orderId);
            }
        }
        cartItemRepository.deleteAll(cartItems);
        if (product != null) {
            productRepository.delete(product);
        }
        memberRepository.deleteAll(members);
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
    @DisplayName("같은 키로 동시에 주문해도 주문은 1건만 생기고 재고는 한 번만 차감된다")
    void createOrder_sameKeyConcurrent() throws Exception {
        // given
        product = product(10);
        Member member = member();
        CartItem cartItem = cartItem(member, 2);
        OrderCreateRequest request = request(cartItem);

        // when
        List<Outcome> outcomes = runConcurrently(i -> () -> orderFacade.createOrder(member.getId(), "same-key", request));

        // then
        assertThat(orderRepository.findIdsByMemberId(member.getId())).hasSize(1);
        assertThat(stock()).isEqualTo(8);
        assertThat(outcomes).filteredOn(Outcome::created).hasSize(1);
        // 먼저 끝난 요청을 본 재시도는 기존 주문을, 겹친 요청은 처리 중·결제 대기 충돌을 받는다
        assertThat(outcomes).filteredOn(outcome -> !outcome.created())
            .allSatisfy(outcome -> assertThat(outcome.errorCode())
                .isIn(null, "COMMON-006", "ORDER-003"));
    }

    // 같은 주문의 중복 예약은 reserveUsage가 아니라 주문 생성의 선행 계약(회원 행 잠금 → 같은 키면 기존 주문 반환)이 막는다.
    // 앞 예약이 첫 적립 건을 다 쓰는 모양으로 만들어, 계약이 깨지면 둘째 적립 건에서 한 번 더 예약되는 것이 보이게 한다
    @Test
    @DisplayName("같은 키로 동시에 포인트를 쓰는 주문을 해도 주문과 포인트 예약은 1건이고 잔액은 한 번만 준다")
    void createOrder_sameKeyConcurrentWithPoint() throws Exception {
        // given — 적립 건 700P(만료 빠름)·1,000P, 잔액 1,700P. 상품 1,500원 × 2에 700P를 쓴다
        product = product(10);
        Member member = member();
        CartItem cartItem = cartItem(member, 2);
        Long[] lotIds = holdPoints(member, 700L, 1_000L);
        OrderCreateRequest request = request(cartItem, 700L);

        // when
        List<Outcome> outcomes = runConcurrently(
            i -> () -> orderFacade.createOrder(member.getId(), "same-key-point", request));

        // then — 주문·재고는 기존 같은 키 테스트와 같다
        assertThat(orderRepository.findIdsByMemberId(member.getId())).hasSize(1);
        assertThat(stock()).isEqualTo(8);
        assertThat(outcomes).filteredOn(Outcome::created).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.created())
            .allSatisfy(outcome -> assertThat(outcome.errorCode())
                .isIn(null, "COMMON-006", "ORDER-003"));
        // 포인트 예약은 한 번이다: 사용 내역 1건 700P, 첫 적립 건은 다 썼고 둘째는 그대로, 잔액은 1,700 − 700
        assertThat(usageCount(member)).isEqualTo(1L);
        assertThat(usageTotal(member)).isEqualTo(700L);
        assertThat(remaining(lotIds[0])).isZero();
        assertThat(remaining(lotIds[1])).isEqualTo(1_000L);
        assertThat(balance(member)).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("한 회원이 다른 키로 동시에 주문해도 결제 대기 주문은 1건만 생기고 재고는 한 번만 차감된다")
    void createOrder_differentKeysConcurrent() throws Exception {
        // given
        product = product(10);
        Member member = member();
        CartItem cartItem = cartItem(member, 2);
        OrderCreateRequest request = request(cartItem);

        // when
        List<Outcome> outcomes = runConcurrently(
            i -> () -> orderFacade.createOrder(member.getId(), "key-" + i, request));

        // then
        assertThat(orderRepository.findIdsByMemberId(member.getId())).hasSize(1);
        assertThat(stock()).isEqualTo(8);
        assertThat(outcomes).filteredOn(Outcome::created).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.created())
            .extracting(Outcome::errorCode)
            .containsOnly("ORDER-003");
    }

    @Test
    @DisplayName("재고 1개 남은 상품을 여러 회원이 동시에 주문하면 1건만 성공하고 재고는 0이 된다")
    void createOrder_lastStockConcurrent() throws Exception {
        // given
        product = product(1);
        List<OrderCreateRequest> requests = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Member member = member();
            requests.add(request(cartItem(member, 1)));
        }

        // when
        List<Outcome> outcomes = runConcurrently(i -> () -> orderFacade.createOrder(
            members.get(i).getId(), "key-" + i, requests.get(i)));

        // then
        assertThat(outcomes).filteredOn(Outcome::created).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.created())
            .extracting(Outcome::errorCode)
            .containsOnly("STOCK-001");
        assertThat(stock()).isZero();
        long orderCount = members.stream()
            .mapToLong(member -> orderRepository.findIdsByMemberId(member.getId()).size())
            .sum();
        assertThat(orderCount).isEqualTo(1);
    }

    private List<Outcome> runConcurrently(IntFunction<Callable<OrderCreateResult>> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<OrderCreateResult>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Callable<OrderCreateResult> call = task.apply(i);
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return call.call();
            }));
        }
        ready.await();
        start.countDown();

        List<Outcome> outcomes = new ArrayList<>();
        for (Future<OrderCreateResult> future : futures) {
            try {
                outcomes.add(new Outcome(future.get(10, TimeUnit.SECONDS).created(), null));
            } catch (ExecutionException e) {
                // 예상한 거절만 결과로 모으고, 그 밖의 예외는 테스트를 실패시킨다
                if (!(e.getCause() instanceof BusinessException businessException)) {
                    throw e;
                }
                outcomes.add(new Outcome(false, businessException.getErrorCode().getCode()));
            }
        }
        executor.shutdown();
        return outcomes;
    }

    private record Outcome(boolean created, String errorCode) {
    }

    private Member member() {
        Member member = memberRepository.save(Member.builder()
            .email(UUID.randomUUID() + "@oc.test")
            .name("동시 주문 회원")
            .phone("010" + String.format("%08d", ThreadLocalRandom.current().nextInt(100000000)))
            .passwordHash("hash")
            .martId(MART_ID)
            .build());
        members.add(member);
        return member;
    }

    private Product product(int stock) {
        return productRepository.save(Product.builder()
            .mart(martRepository.findById(MART_ID).orElseThrow())
            .name("애호박")
            .price(1500L)
            .saleUnitAmount(new BigDecimal("1"))
            .saleUnit(SaleUnit.EA)
            .totalQuantity(stock)
            .build());
    }

    private CartItem cartItem(Member member, int quantity) {
        CartItem cartItem = cartItemRepository.save(CartItem.builder()
            .memberId(member.getId())
            .productId(product.getId())
            .quantity(quantity)
            .build());
        cartItems.add(cartItem);
        return cartItem;
    }

    private OrderCreateRequest request(CartItem cartItem) {
        return new OrderCreateRequest(List.of(cartItem.getId()), "홍길동", "01012345678", "서울 종로구 삼청로 1",
            "101호", LEGAL_DONG_CODE);
    }

    private OrderCreateRequest request(CartItem cartItem, long usedPoint) {
        return new OrderCreateRequest(List.of(cartItem.getId()), "홍길동", "01012345678", "서울 종로구 삼청로 1",
            "101호", LEGAL_DONG_CODE, usedPoint);
    }

    // 확정된 적립 건 두 개(첫째가 하루 먼저 만료)와 두 잔량의 합과 같은 잔액을 만든다. 만료가 빠른 건부터 쓰인다
    private Long[] holdPoints(Member member, long earlyAmount, long lateAmount) {
        LocalDateTime now = LocalDateTime.now(clock);
        PointEarning early = PointEarning.pending(member.getId(), EARLY_LOT_ORDER_ID, earlyAmount);
        early.complete(now.minusDays(2));
        PointEarning late = PointEarning.pending(member.getId(), LATE_LOT_ORDER_ID, lateAmount);
        late.complete(now.minusDays(1));
        pointEarningRepository.save(early);
        pointEarningRepository.save(late);
        memberPointRepository.save(
            MemberPoint.builder().memberId(member.getId()).balance(earlyAmount + lateAmount).build());
        return new Long[] {early.getId(), late.getId()};
    }

    private long usageCount(Member member) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM point_usage pu "
                + "JOIN point_earning pe ON pu.point_earning_id = pe.id WHERE pe.member_id = ?",
            Long.class, member.getId());
    }

    private long usageTotal(Member member) {
        return jdbcTemplate.queryForObject("SELECT COALESCE(SUM(pu.amount), 0) FROM point_usage pu "
                + "JOIN point_earning pe ON pu.point_earning_id = pe.id WHERE pe.member_id = ?",
            Long.class, member.getId());
    }

    private long remaining(Long lotId) {
        return jdbcTemplate.queryForObject("SELECT remaining_amount FROM point_earning WHERE id = ?",
            Long.class, lotId);
    }

    private long balance(Member member) {
        return jdbcTemplate.queryForObject("SELECT balance FROM member_point WHERE member_id = ?",
            Long.class, member.getId());
    }

    private int stock() {
        return productRepository.findById(product.getId()).orElseThrow().getTotalQuantity();
    }
}
