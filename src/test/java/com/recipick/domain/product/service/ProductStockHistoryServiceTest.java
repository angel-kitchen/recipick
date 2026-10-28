package com.recipick.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.product.dto.StockAdjustmentHistoryResponse;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.StockAdjustment;
import com.recipick.domain.product.entity.StockAdjustmentType;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.domain.product.repository.StockAdjustmentRepository;
import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductStockHistoryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockAdjustmentRepository stockAdjustmentRepository;

    @Mock
    private com.recipick.global.audit.AdminAuditRecorder auditRecorder;

    @InjectMocks
    private ProductStockService productStockService;

    private final Pageable pageable = PageRequest.of(0, 20);

    private Product product(Long id, Long martId, boolean deleted) {
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
            .totalQuantity(10)
            .deleted(deleted)
            .build();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private StockAdjustment adjustment(Product product, StockAdjustmentType type,
                                       int quantity, int beforeQuantity, String reason) {
        return StockAdjustment.builder()
            .product(product)
            .type(type)
            .quantity(quantity)
            .beforeQuantity(beforeQuantity)
            .reason(reason)
            .adminId(3L)
            .build();
    }

    @Test
    @DisplayName("이력 조회 : 이력을 응답으로 바꿔 페이지 정보와 함께 돌려준다")
    void getStockAdjustments() {
        // given
        Product product = product(1L, 1L, false);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        List<StockAdjustment> adjustments = List.of(
            adjustment(product, StockAdjustmentType.DECREASE, 3, 80, "폐기"),
            adjustment(product, StockAdjustmentType.INCREASE, 30, 50, "입고"));
        given(stockAdjustmentRepository.findByProductIdOrderByCreatedAtDescIdDesc(1L, pageable))
            .willReturn(new PageImpl<>(adjustments, pageable, 2));

        // when
        PageResponse<StockAdjustmentHistoryResponse> result =
            productStockService.getStockAdjustments(AdminRole.SUPER_ADMIN, null, 1L, pageable);

        // then
        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).type()).isEqualTo(StockAdjustmentType.DECREASE);
        assertThat(result.content().get(0).quantity()).isEqualTo(3);
        assertThat(result.content().get(0).beforeQuantity()).isEqualTo(80);
        assertThat(result.content().get(0).reason()).isEqualTo("폐기");
        assertThat(result.content().get(0).adminId()).isEqualTo(3L);
        assertThat(result.content().get(1).type()).isEqualTo(StockAdjustmentType.INCREASE);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.page()).isZero();
    }

    @Test
    @DisplayName("이력 조회 : 이력이 없으면 빈 목록을 돌려준다")
    void getStockAdjustments_empty() {
        // given
        Product product = product(1L, 1L, false);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(stockAdjustmentRepository.findByProductIdOrderByCreatedAtDescIdDesc(1L, pageable))
            .willReturn(new PageImpl<>(List.of(), pageable, 0));

        // when
        PageResponse<StockAdjustmentHistoryResponse> result =
            productStockService.getStockAdjustments(AdminRole.SUPER_ADMIN, null, 1L, pageable);

        // then
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    @DisplayName("이력 조회 : MART_ADMIN은 본인 마트 상품의 이력을 조회할 수 있다")
    void getStockAdjustments_martAdminOwnMart() {
        // given
        Product product = product(1L, 1L, false);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(stockAdjustmentRepository.findByProductIdOrderByCreatedAtDescIdDesc(1L, pageable))
            .willReturn(new PageImpl<>(List.of(), pageable, 0));

        // when
        PageResponse<StockAdjustmentHistoryResponse> result =
            productStockService.getStockAdjustments(AdminRole.MART_ADMIN, 1L, 1L, pageable);

        // then
        assertThat(result.content()).isEmpty();
        verify(stockAdjustmentRepository)
            .findByProductIdOrderByCreatedAtDescIdDesc(1L, pageable);
    }

    @Test
    @DisplayName("이력 조회 : MART_ADMIN이 다른 마트 상품이면 MART_ACCESS_DENIED이고 이력을 조회하지 않는다")
    void getStockAdjustments_martAdminOtherMart() {
        // given
        Product product = product(1L, 2L, false);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        // when
        Throwable thrown = catchThrowable(
            () -> productStockService.getStockAdjustments(AdminRole.MART_ADMIN, 1L, 1L, pageable));

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(AuthErrorCode.MART_ACCESS_DENIED);
        verify(stockAdjustmentRepository, never()).findByProductIdOrderByCreatedAtDescIdDesc(any(), any());
    }

    @Test
    @DisplayName("이력 조회 : 없는 상품이거나 삭제된 상품이면 PRODUCT_NOT_FOUND")
    void getStockAdjustments_notFound() {
        // given
        given(productRepository.findById(99L)).willReturn(Optional.empty());
        given(productRepository.findById(1L)).willReturn(Optional.of(product(1L, 1L, true)));

        // when
        Throwable missingThrown = catchThrowable(
            () -> productStockService.getStockAdjustments(AdminRole.SUPER_ADMIN, null, 99L, pageable));
        Throwable deletedThrown = catchThrowable(
            () -> productStockService.getStockAdjustments(AdminRole.SUPER_ADMIN, null, 1L, pageable));

        // then
        assertThat(missingThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
        assertThat(deletedThrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
        verify(stockAdjustmentRepository, never()).findByProductIdOrderByCreatedAtDescIdDesc(any(), any());
    }
}
