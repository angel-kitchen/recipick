package com.recipick.domain.order.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.order.dto.OrderCreateResult;
import com.recipick.domain.order.dto.request.OrderCreateRequest;
import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.order.repository.CartItemRepository;
import com.recipick.domain.order.repository.OrderItemRepository;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
import com.recipick.domain.payment.repository.PaymentRepository;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

// 탈퇴 기능이 아직 없어, 회원 행을 잠근 채 주문 생성이 잠금에 걸리길 기다렸다가 작업하고 커밋하는 트랜잭션으로 탈퇴 경합을 흉내 낸다
class OrderCreateMemberLockTest extends IntegrationTestSupport {

    // data.sql 시연 데이터: 1번 마트는 삼청동(1111014000)
    private static final Long MART_ID = 1L;
    private static final String LEGAL_DONG_CODE = "1111014000";
    private static final long WAIT_SECONDS = 5;
    private static final long POLL_MILLIS = 20;

    @Autowired
    private OrderFacade orderFacade;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManager em;
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

    private Member member;
    private Product product;
    private CartItem cartItem;

    @AfterEach
    void cleanUp() {
        for (Long orderId : orderRepository.findIdsByMemberId(member.getId())) {
            paymentRepository.findByOrderId(orderId).ifPresent(paymentRepository::delete);
            orderStatusHistoryRepository.deleteAll(orderStatusHistoryRepository.findAllByOrderId(orderId));
            orderItemRepository.deleteAll(orderItemRepository.findAllByOrderId(orderId));
            orderRepository.deleteById(orderId);
        }
        cartItemRepository.delete(cartItem);
        productRepository.delete(product);
        memberRepository.findById(member.getId()).ifPresent(memberRepository::delete);
    }

    @Test
    @DisplayName("회원 행을 잠근 트랜잭션이 회원을 지우고 끝나면 기다리던 주문 생성은 회원 없음으로 거절되고 재고도 그대로다")
    void createOrder_rejectsMemberDeletedWhileWaiting() throws Exception {
        // given
        OrderCreateRequest request = prepare(10);
        CountDownLatch locked = new CountDownLatch(1);
        Future<?> withdrawal = lockMemberThen(locked, () -> memberRepository.deleteById(member.getId()));
        assertThat(locked.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        // when
        assertThatThrownBy(() -> orderFacade.createOrder(member.getId(), "key", request))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
        withdrawal.get(WAIT_SECONDS, TimeUnit.SECONDS);

        // then
        assertThat(orderRepository.findIdsByMemberId(member.getId())).isEmpty();
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    @DisplayName("회원 행을 잠근 트랜잭션이 탈퇴 상태를 저장하고 끝나면 기다리던 주문 생성은 회원 없음으로 거절되고 재고도 그대로다")
    void createOrder_rejectsMemberWithdrawnWhileWaiting() throws Exception {
        // given
        OrderCreateRequest request = prepare(10);
        CountDownLatch locked = new CountDownLatch(1);
        Future<?> withdrawal = lockMemberThen(locked, () -> em.createNativeQuery("UPDATE member SET withdrawn_at = CURRENT_TIMESTAMP WHERE id = :id")
                        .setParameter("id", member.getId()).executeUpdate());
        assertThat(locked.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        // when
        assertThatThrownBy(() -> orderFacade.createOrder(member.getId(), "key", request))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
        withdrawal.get(WAIT_SECONDS, TimeUnit.SECONDS);

        // then
        assertThat(orderRepository.findIdsByMemberId(member.getId())).isEmpty();
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    @DisplayName("회원 행을 잠근 트랜잭션이 같은 키의 주문을 만들고 끝나면 기다리던 요청은 새로 만들지 않고 기존 주문을 돌려준다")
    void createOrder_returnsOrderCommittedWhileWaiting() throws Exception {
        // given
        OrderCreateRequest request = prepare(10);
        CountDownLatch locked = new CountDownLatch(1);
        Future<?> first = lockMemberThen(locked,
                () -> orderFacade.createOrder(member.getId(), "same-key", request));
        assertThat(locked.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        // when
        OrderCreateResult retried = orderFacade.createOrder(member.getId(), "same-key", request);
        first.get(WAIT_SECONDS, TimeUnit.SECONDS);

        // then: 잠금 뒤에 스냅샷이 만들어져야 앞 트랜잭션이 커밋한 주문을 멱등 조회로 찾는다
        assertThat(retried.created()).isFalse();
        assertThat(orderRepository.findIdsByMemberId(member.getId()))
                .containsExactly(retried.response().orderId());
        assertThat(stock()).isEqualTo(8);
    }

    // 회원 행을 잠그고 신호를 보낸 뒤, 테스트 스레드의 주문 생성이 그 잠금을 기다리는 것을 확인하고 나서 작업하고 커밋한다.
    // 고정 시간만 기다리면 테스트 스레드가 늦을 때 경합 없이 통과할 수 있어서 대기 중인 요청을 직접 확인한다
    private Future<?> lockMemberThen(CountDownLatch locked, Runnable action) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            memberRepository.findByIdForUpdate(member.getId()).orElseThrow();
            locked.countDown();
            awaitMemberLockWaiter();
            action.run();
        }));
        executor.shutdown();
        return future;
    }

    // 같은 계정의 연결은 PROCESSLIST에 보인다. 회원 행은 이 트랜잭션이 잠그고 있으므로 다른 연결의 회원 FOR UPDATE 문이
    // 떠 있다는 것은 그 요청이 잠금 대기 중이라는 뜻이다
    private void awaitMemberLockWaiter() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (countMemberLockWaiters() == 0) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("주문 생성이 회원 잠금을 기다리지 않습니다.");
            }
            sleepQuietly(POLL_MILLIS);
        }
    }

    private long countMemberLockWaiters() {
        Number count = (Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM information_schema.PROCESSLIST "
                                + "WHERE ID <> CONNECTION_ID() AND INFO LIKE '%from member %for update%'")
                .getSingleResult();
        return count.longValue();
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private OrderCreateRequest prepare(int stock) {
        product = product(stock);
        member = member();
        cartItem = cartItem(member, 2);
        return new OrderCreateRequest(List.of(cartItem.getId()), "홍길동", "01012345678", "서울 종로구 삼청로 1",
                "101호", LEGAL_DONG_CODE);
    }

    private Member member() {
        return memberRepository.save(Member.builder()
                .email(UUID.randomUUID() + "@ml.test")
                .name("회원 잠금 테스트 회원")
                .phone("010" + String.format("%08d", ThreadLocalRandom.current().nextInt(100000000)))
                .passwordHash("hash")
                .martId(MART_ID)
                .build());
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
        return cartItemRepository.save(CartItem.builder()
                .memberId(member.getId())
                .productId(product.getId())
                .quantity(quantity)
                .build());
    }

    private int stock() {
        return productRepository.findById(product.getId()).orElseThrow().getTotalQuantity();
    }
}
