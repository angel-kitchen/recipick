package com.recipick.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.product.dto.StockQuantity;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProductStockConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    ProductStockService productStockService;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    MartRepository martRepository;

    private Long martId;
    private Long productId;

    @AfterEach
    void cleanUp() {
        if (productId != null) {
            productRepository.deleteById(productId);
        }
        if (martId != null) {
            martRepository.deleteById(martId);
        }
    }

    @Test
    @DisplayName("재고 5개 상품에 10건이 동시에 1개씩 차감하면 5건만 성공하고 재고는 0이다")
    void decreaseStocks_concurrent() throws Exception {
        // given
        Mart mart = martRepository.save(Mart.builder()
            .regionId(31L)
            .name("동시성 테스트 마트")
            .latitude(new BigDecimal("37.3670000"))
            .longitude(new BigDecimal("127.1080000"))
            .serviceRadiusM(2000)
            .build());
        Product product = productRepository.save(Product.builder()
            .mart(mart)
            .name("애호박")
            .totalQuantity(5)
            .deleted(false)
            .build());
        martId = mart.getId();
        productId = product.getId();

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();

        // when
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    productStockService.decreaseStocks(martId,
                        List.of(new StockQuantity(productId, 1)));
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ProductErrorCode.INSUFFICIENT_STOCK) {
                        insufficient.incrementAndGet();
                    }
                }
                return null;
            }));
        }
        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // then
        assertThat(success.get()).isEqualTo(5);
        assertThat(insufficient.get()).isEqualTo(5);
        assertThat(productRepository.findById(productId).orElseThrow().getTotalQuantity()).isZero();
    }
}
