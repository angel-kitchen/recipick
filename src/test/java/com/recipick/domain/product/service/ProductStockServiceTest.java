package com.recipick.domain.product.service;

import com.recipick.domain.product.dto.StockQuantity;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProductStockServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductStockService productStockService;

    private Mart mart(Long id){
        Mart mart = Mart.builder()
            .regionId(31L)
            .name("삼청동 마트")
            .latitude(new BigDecimal("37.3670000"))
            .longitude(new BigDecimal("127.1080000"))
            .serviceRadiusM(2000)
            .build();
        ReflectionTestUtils.setField(mart, "id", id);
        return mart;
    }

    private Product product(Long id,
                            Mart mart,
                            int totalQuantity,
                            boolean deleted){
        Product product = Product.builder()
            .mart(mart)
            .name("애호박")
            .totalQuantity(totalQuantity)
            .deleted(deleted)
            .build();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    @Test
    @DisplayName("차감 : 모든 상품이 충분하면 각각 차감된다")
    void decreaseStock(){
        Mart mart = mart(1L);
        Product p1 = product(1L, mart, 10, false);
        Product p2 = product(2L, mart, 5, false);
        given(productRepository.findAllByIdInForUpdate(List.of(1L,2L))).willReturn(List.of(p1,p2));

        productStockService.decreaseStocks(1L,
            List.of(new StockQuantity(1L,3), new StockQuantity(2L,5)));

        assertThat(p1.getTotalQuantity()).isEqualTo(7);
        assertThat(p2.getTotalQuantity()).isZero();
    }

    @Test
    @DisplayName("차감 : 재고가 부족하면 INSUFFICIENT_STOCK")
    void decreaseStock_insufficientStock(){
        Mart mart = mart(1L);
        Product p1 = product(1L, mart, 2, false);
        given(productRepository.findAllByIdInForUpdate(List.of(1L))).willReturn(List.of(p1));

    assertThatThrownBy(() -> productStockService.decreaseStocks(1L, List.of(new StockQuantity(1L,3))))
        .extracting(e-> ((BusinessException) e).getErrorCode())
        .isEqualTo(ProductErrorCode.INSUFFICIENT_STOCK);
    }

    @Test
    @DisplayName("차감: 없는 상품이면 PRODUCT_NOT_FOUND")
    void decreaseStock_notFound() {
        given(productRepository.findAllByIdInForUpdate(List.of(1L))).willReturn(List.of());

        assertThatThrownBy(() -> productStockService.decreaseStocks(1L, List.of(new StockQuantity(1L, 1))))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("차감: 삭제된 상품이면 PRODUCT_NOT_FOUND")
    void decreaseStock_deleted() {
        Product p1 = product(1L, mart(1L), 10, true);
        given(productRepository.findAllByIdInForUpdate(List.of(1L))).willReturn(List.of(p1));

        assertThatThrownBy(() -> productStockService.decreaseStocks(1L, List.of(new StockQuantity(1L, 1))))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("차감: 다른 마트의 상품이면 PRODUCT_NOT_FOUND")
    void decreaseStock_otherMart() {
        Product p1 = product(1L, mart(1L), 10, false);
        given(productRepository.findAllByIdInForUpdate(List.of(1L))).willReturn(List.of(p1));

        assertThatThrownBy(() -> productStockService.decreaseStocks(2L, List.of(new StockQuantity(1L, 1))))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("차감: 판매 중지 상품이면 PRODUCT_NOT_SELLING")
    void decreaseStock_notSelling() {
        Product p1 = product(1L, mart(1L), 10, false);
        ReflectionTestUtils.setField(p1, "status", ProductStatus.STOPPED);
        given(productRepository.findAllByIdInForUpdate(List.of(1L))).willReturn(List.of(p1));

        assertThatThrownBy(() -> productStockService.decreaseStocks(1L, List.of(new StockQuantity(1L, 1))))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.PRODUCT_NOT_SELLING);
    }

    @Test
    @DisplayName("차감: 같은 상품이 두 번 들어오면 잠금 조회는 한 번만 하고 합계만큼 차감된다")
    void decreaseStock_duplicatedItems() {
        Product p1 = product(1L, mart(1L), 10, false);
        given(productRepository.findAllByIdInForUpdate(List.of(1L))).willReturn(List.of(p1));

        productStockService.decreaseStocks(1L,
            List.of(new StockQuantity(1L, 2), new StockQuantity(1L, 3)));

        verify(productRepository).findAllByIdInForUpdate(List.of(1L));
        assertThat(p1.getTotalQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("복구: 수량만큼 늘어난다")
    void increaseStock() {
        Product p1 = product(1L, mart(1L), 7, false);
        given(productRepository.findAllByIdInForUpdate(List.of(1L))).willReturn(List.of(p1));

        productStockService.increaseStocks(List.of(new StockQuantity(1L, 3)));

        assertThat(p1.getTotalQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("복구: 삭제된 상품도 복구된다")
    void increaseStock_deleted() {
        Product p1 = product(1L, mart(1L), 7, true);
        given(productRepository.findAllByIdInForUpdate(List.of(1L))).willReturn(List.of(p1));

        productStockService.increaseStocks(List.of(new StockQuantity(1L, 3)));

        assertThat(p1.getTotalQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("복구: 없는 상품이면 PRODUCT_NOT_FOUND")
    void increaseStock_notFound() {
        given(productRepository.findAllByIdInForUpdate(List.of(1L))).willReturn(List.of());

        assertThatThrownBy(() -> productStockService.increaseStocks(List.of(new StockQuantity(1L, 1))))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
    }
}
