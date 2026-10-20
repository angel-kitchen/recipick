package com.recipick.domain.product.service;

import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProductTest {

    private Product product(int stock) {
        return Product.builder().totalQuantity(stock).build();
    }


    @Test
    @DisplayName("차감: 재고 안에서 차감하면 그만큼 줄어든다")
    void decreaseStocks() {
        Product product = product(10);

        product.decreaseStock(3);

        assertThat(product.getTotalQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("차감: 재고와 같은 수량은 차감할 수 있고 0이 된다")
    void decreaseStocks_exact() {
        Product product = product(5);

        product.decreaseStock(5);

        assertThat(product.getTotalQuantity()).isZero();
    }

    @Test
    @DisplayName("차감: 재고보다 많으면 INSUFFICIENT_STOCK이고 재고는 그대로다")
    void decreaseStocks_insufficient() {
        Product product = product(2);

        assertThatThrownBy(() -> product.decreaseStock(3))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.INSUFFICIENT_STOCK);
        assertThat(product.getTotalQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("복구: 수량만큼 늘어난다")
    void increaseStocks() {
        Product product = product(7);

        product.increaseStock(3);

        assertThat(product.getTotalQuantity()).isEqualTo(10);
    }
}
