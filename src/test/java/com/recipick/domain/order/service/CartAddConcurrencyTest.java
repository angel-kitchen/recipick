package com.recipick.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.order.dto.request.CartItemAddRequest;
import com.recipick.domain.order.entity.CartItem;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.facade.CartFacade;
import com.recipick.domain.order.repository.CartAddHistoryRepository;
import com.recipick.domain.order.repository.CartItemRepository;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CartAddConcurrencyTest extends IntegrationTestSupport {

    private static final Long MEMBER_ID = 9101L;
    private static final Long PRODUCT_ID = 9201L;
    private static final int THREADS = 10;

    @Autowired
    CartService cartService;
    @Autowired
    CartItemRepository cartItemRepository;
    @Autowired
    CartAddHistoryRepository cartAddHistoryRepository;
    @Autowired
    CartFacade cartFacade;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    MartRepository martRepository;

    private final List<Long> memberIds = new ArrayList<>();
    private Long productId;
    private Long martId;

    @AfterEach
    void cleanUp() {
        cartItemRepository.findByMemberIdAndProductId(MEMBER_ID, PRODUCT_ID).ifPresent(cartItemRepository::delete);
        cartItemRepository.deleteAll(cartItemRepository.findAll().stream()
                .filter(item -> memberIds.contains(item.getMemberId()))
                .toList());
        cartAddHistoryRepository.deleteAll(cartAddHistoryRepository.findAll().stream()
                .filter(history -> history.getMemberId().equals(MEMBER_ID) || memberIds.contains(history.getMemberId()))
                .toList());
        memberRepository.deleteAllById(memberIds);
        if (productId != null) {
            productRepository.deleteById(productId);
        }
        if (martId != null) {
            martRepository.deleteById(martId);
        }
    }

    @Test
    @DisplayName("이미 담긴 상품을 10건 동시에 담아도 수량이 빠짐없이 합산되고 이력도 10건 남는다")
    void addCartItem_concurrentMerge() throws Exception {
        // given
        cartItemRepository.save(CartItem.builder()
                .memberId(MEMBER_ID)
                .productId(PRODUCT_ID)
                .quantity(1)
                .build());

        // when
        List<Throwable> failures = runConcurrently(() -> cartService.addCartItem(MEMBER_ID, PRODUCT_ID, 2, 99));

        // then
        assertThat(failures).isEmpty();
        assertThat(cartItemRepository.findByMemberIdAndProductId(MEMBER_ID, PRODUCT_ID).orElseThrow().getQuantity())
                .isEqualTo(1 + 2 * THREADS);
        assertThat(countHistories()).isEqualTo(THREADS);
    }

    @Test
    @DisplayName("처음 담는 상품을 동시에 담으면 한 행만 생기고, 실패한 요청은 CART_CONCURRENT_MODIFICATION이며 이력이 없다")
    void addCartItem_concurrentFirstInsert() throws Exception {
        // when
        List<Throwable> failures = runConcurrently(() -> cartService.addCartItem(MEMBER_ID, PRODUCT_ID, 1, 99));

        // then
        assertThat(failures).allSatisfy(failure -> assertThat(failure)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(OrderErrorCode.CART_CONCURRENT_MODIFICATION));
        int succeeded = THREADS - failures.size();
        assertThat(cartItemRepository.findByMemberIdAndProductId(MEMBER_ID, PRODUCT_ID).orElseThrow().getQuantity())
                .isEqualTo(succeeded);
        assertThat(countHistories()).isEqualTo(succeeded);
    }

    @Test
    @DisplayName("재고 5개인 상품을 서로 다른 회원 10명이 동시에 5개씩 담아도 모두 성공하고 재고는 그대로다")
    void addCartItem_concurrentDifferentMembers() throws Exception {
        // given
        Mart mart = martRepository.save(Mart.builder()
                .regionId(32L)
                .name("장바구니 동시성 테스트 마트")
                .latitude(new BigDecimal("37.3670000"))
                .longitude(new BigDecimal("127.1080000"))
                .serviceRadiusM(2000)
                .build());
        Product product = productRepository.save(Product.builder()
                .mart(mart)
                .name("애호박")
                .price(1500L)
                .totalQuantity(5)
                .deleted(false)
                .build());
        martId = mart.getId();
        productId = product.getId();
        for (int i = 0; i < THREADS; i++) {
            memberIds.add(memberRepository.save(Member.builder()
                    .email("cart-concurrency-" + i + "@example.com")
                    .name("회원" + i)
                    .phone("0109100000" + i)
                    .passwordHash("hash")
                    .martId(martId)
                    .build()).getId());
        }
        AtomicInteger index = new AtomicInteger();

        // when
        List<Throwable> failures = runConcurrently(() -> cartFacade.addCartItem(
                memberIds.get(index.getAndIncrement()), new CartItemAddRequest(productId, 5)));

        // then
        assertThat(failures).isEmpty();
        assertThat(cartItemRepository.findAll().stream()
                .filter(item -> memberIds.contains(item.getMemberId())))
                .hasSize(THREADS)
                .allSatisfy(item -> assertThat(item.getQuantity()).isEqualTo(5));
        assertThat(productRepository.findById(productId).orElseThrow().getTotalQuantity()).isEqualTo(5);
    }

    private List<Throwable> runConcurrently(Callable<?> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return task.call();
            }));
        }
        ready.await();
        start.countDown();
        List<Throwable> failures = new ArrayList<>();
        for (Future<?> future : futures) {
            try {
                future.get(10, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            }
        }
        executor.shutdown();
        return failures;
    }

    private long countHistories() {
        return cartAddHistoryRepository.findAll().stream()
                .filter(history -> history.getMemberId().equals(MEMBER_ID))
                .count();
    }
}
