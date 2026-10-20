package com.recipick.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.product.dto.StockQuantity;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.domain.product.service.ProductStockService;
import com.recipick.global.response.ErrorResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

class LockTimeoutTest extends IntegrationTestSupport {

    @Autowired
    ProductStockService productStockService;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    MartRepository martRepository;
    @Autowired
    TransactionTemplate transactionTemplate;
    @Autowired
    GlobalExceptionHandler globalExceptionHandler;

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
    @DisplayName("다른 트랜잭션이 잠근 상품을 5초 넘게 기다리면 409 COMMON-010으로 응답한다")
    void lockTimeout_returnsCommon010() throws Exception {
        // given
        Mart mart = martRepository.save(Mart.builder()
            .regionId(32L)
            .name("락 대기 테스트 마트")
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

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> holder = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            productRepository.findAllByIdInForUpdate(List.of(productId));
            locked.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

        // when
        long startedAt = System.nanoTime();
        Throwable thrown = catchThrowable(
            () -> productStockService.decreaseStocks(martId, List.of(new StockQuantity(productId, 1))));
        long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        release.countDown();
        holder.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(thrown).isInstanceOf(PessimisticLockingFailureException.class);
        // H2 기본값(1초)이 아니라 팀 공통값(5초)이 적용됐는지 확인한다.
        assertThat(waitedMillis).isGreaterThanOrEqualTo(4_500);
        ResponseEntity<ErrorResponse> response =
            globalExceptionHandler.handlePessimisticLockingFailure((PessimisticLockingFailureException) thrown);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("COMMON-010");
    }
}
