package com.recipick.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.product.dto.StockAdjustmentRequest;
import com.recipick.domain.product.dto.StockAdjustmentResponse;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.StockAdjustment;
import com.recipick.domain.product.entity.StockAdjustmentType;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.domain.product.repository.StockAdjustmentRepository;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ProductStockAdjustServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockAdjustmentRepository stockAdjustmentRepository;

    @Mock
    private com.recipick.global.audit.AdminAuditRecorder auditRecorder;

    @InjectMocks
    private ProductStockService productStockService;

    private Product product(Long id, Long martId, int totalQuantity, boolean deleted) {
        Mart mart = Mart.builder()
            .regionId(1L)
            .name("삼청 마트")
            .latitude(new BigDecimal("37.5000000"))
            .longitude(new BigDecimal("127.0000000"))
            .serviceRadiusM(2000)
            .build();
        ReflectionTestUtils.setField(mart, "id", martId);

        Product product = Product.builder()
            .mart(mart)
            .name("애호박")
            .totalQuantity(totalQuantity)
            .deleted(deleted)
            .build();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private StockAdjustmentRequest request(StockAdjustmentType type, int quantity){
        return new StockAdjustmentRequest(type, quantity, "사유");
    }

    @Test
    @DisplayName("재고 증감 : 증가하면 재고가 늘고 이력이 저장된다")
    void adjustStock_increase() {
        // given
        Product product = product(1L, 1L, 50, false);
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product));
        StockAdjustmentRequest request = new StockAdjustmentRequest(StockAdjustmentType.INCREASE, 30, "입고");

        // when
        StockAdjustmentResponse result =
            productStockService.adjustStock(AdminRole.SUPER_ADMIN, null, 9L, 1L, request);

        // then
        assertThat(result.beforeQuantity()).isEqualTo(50);
        assertThat(result.afterQuantity()).isEqualTo(80);
        assertThat(product.getTotalQuantity()).isEqualTo(80);

        ArgumentCaptor<StockAdjustment> captor = ArgumentCaptor.forClass(StockAdjustment.class);
        verify(stockAdjustmentRepository).save(captor.capture());
        StockAdjustment saved = captor.getValue();
        assertThat(saved.getProduct()).isSameAs(product);
        assertThat(saved.getType()).isEqualTo(StockAdjustmentType.INCREASE);
        assertThat(saved.getQuantity()).isEqualTo(30);
        assertThat(saved.getBeforeQuantity()).isEqualTo(50);
        assertThat(saved.getReason()).isEqualTo("입고");
        assertThat(saved.getAdminId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("재고 증감 : 감소하면 재고가 줄고, 현재 재고와 같은 수량까지 줄일 수 있다")
    void adjustStock_decrease() {
        // given
        Product product = product(1L, 1L, 10, false);
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product));

        // when
        StockAdjustmentResponse partial = productStockService.adjustStock(
            AdminRole.SUPER_ADMIN, null, 9L, 1L, request(StockAdjustmentType.DECREASE, 3));
        StockAdjustmentResponse all = productStockService.adjustStock(
            AdminRole.SUPER_ADMIN, null, 9L, 1L, request(StockAdjustmentType.DECREASE, 7));

        // then
        assertThat(partial.beforeQuantity()).isEqualTo(10);
        assertThat(partial.afterQuantity()).isEqualTo(7);
        assertThat(all.afterQuantity()).isZero();
        assertThat(product.getTotalQuantity()).isZero();
    }

    @Test
    @DisplayName("재고 증감 : 현재 재고보다 많이 줄이면 STOCK_ADJUST_EXCEEDS_AVAILABLE이고 저장하지 않는다")
    void adjustStock_exceedsAvailable() {
        // given
        Product product = product(1L, 1L, 5, false);
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product));
        StockAdjustmentRequest request = request(StockAdjustmentType.DECREASE, 6);

        // when
        Throwable thrown = catchThrowable(
            () -> productStockService.adjustStock(AdminRole.SUPER_ADMIN, null, 9L, 1L, request));

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.STOCK_ADJUST_EXCEEDS_AVAILABLE);
        assertThat(product.getTotalQuantity()).isEqualTo(5);
        verify(stockAdjustmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("재고 증감 : 수량이 0 이하면 INVALID_ADJUST_QUANTITY")
    void adjustStock_invalidQuantity() {
        // given
        Product product = product(1L, 1L, 5, false);
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product));
        StockAdjustmentRequest increase = request(StockAdjustmentType.INCREASE, 0);
        StockAdjustmentRequest decrease = request(StockAdjustmentType.DECREASE, -1);

        // when
        Throwable increaseThrown = catchThrowable(
            () -> productStockService.adjustStock(AdminRole.SUPER_ADMIN, null, 9L, 1L, increase));
        Throwable decreaseThrown = catchThrowable(
            () -> productStockService.adjustStock(AdminRole.SUPER_ADMIN, null, 9L, 1L, decrease));

        // then
        assertThat(increaseThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.INVALID_ADJUST_QUANTITY);
        assertThat(decreaseThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.INVALID_ADJUST_QUANTITY);
        verify(stockAdjustmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("재고 증감 : MART_ADMIN은 본인 마트 상품의 재고를 조정할 수 있다")
    void adjustStock_martAdminOwnMart() {
        // given
        Product product = product(1L, 1L, 5, false);
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product));

        // when
        productStockService.adjustStock(
            AdminRole.MART_ADMIN, 1L, 9L, 1L, request(StockAdjustmentType.INCREASE, 5));

        // then
        assertThat(product.getTotalQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("재고 증감 : MART_ADMIN이 다른 마트 상품이면 MART_ACCESS_DENIED이고 재고는 그대로다")
    void adjustStock_martAdminOtherMart() {
        // given
        Product product = product(1L, 2L, 5, false);
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(product));
        StockAdjustmentRequest request = request(StockAdjustmentType.INCREASE, 5);

        // when
        Throwable thrown = catchThrowable(
            () -> productStockService.adjustStock(AdminRole.MART_ADMIN, 1L, 9L, 1L, request));

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(AuthErrorCode.MART_ACCESS_DENIED);
        assertThat(product.getTotalQuantity()).isEqualTo(5);
        verify(stockAdjustmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("재고 증감 : 없는 상품이거나 삭제된 상품이면 PRODUCT_NOT_FOUND")
    void adjustStock_notFound() {
        // given
        given(productRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());
        Product deleted = product(1L, 1L, 5, true);
        given(productRepository.findByIdForUpdate(1L)).willReturn(Optional.of(deleted));
        StockAdjustmentRequest request = request(StockAdjustmentType.INCREASE, 5);

        // when
        Throwable missingThrown = catchThrowable(
            () -> productStockService.adjustStock(AdminRole.SUPER_ADMIN, null, 9L, 99L, request));
        Throwable deletedThrown = catchThrowable(
            () -> productStockService.adjustStock(AdminRole.SUPER_ADMIN, null, 9L, 1L, request));

        // then
        assertThat(missingThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
        assertThat(deletedThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
    }
}
