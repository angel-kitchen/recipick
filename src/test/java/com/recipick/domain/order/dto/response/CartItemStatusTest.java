package com.recipick.domain.order.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.entity.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CartItemStatusTest {

    private static final Long MART_ID = 7L;
    private static final Long OTHER_MART_ID = 8L;

    @ParameterizedTest(name = "[{index}] {5}")
    @CsvSource({
            "7, false, SELLING, 10, 2, AVAILABLE",
            "7, false, SELLING, 2, 2, AVAILABLE",
            "7, false, SELLING, 1, 2, INSUFFICIENT_STOCK",
            "7, false, SELLING, 0, 2, SOLD_OUT",
            "7, false, STOPPED, 10, 2, STOPPED",
            "7, true, SELLING, 10, 2, DELETED",
            "8, false, SELLING, 10, 2, PREVIOUS_MART"
    })
    @DisplayName("상품의 마트·삭제·판매 상태·재고와 담은 수량으로 장바구니 항목 상태를 판정한다")
    void of(Long productMartId, boolean deleted, ProductStatus status, int stock, int quantity,
            CartItemStatus expected) {
        // given
        ProductSummary product = product(productMartId, deleted, status, stock);

        // when
        CartItemStatus result = CartItemStatus.of(product, MART_ID, quantity);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "8, true, STOPPED, 0, PREVIOUS_MART",
            "7, true, STOPPED, 0, DELETED",
            "7, false, STOPPED, 0, STOPPED"
    })
    @DisplayName("여러 조건이 겹치면 이전 마트, 삭제, 판매 중지, 품절 순으로 앞선 상태를 돌려준다")
    void of_precedence(Long productMartId, boolean deleted, ProductStatus status, int stock,
            CartItemStatus expected) {
        // given
        ProductSummary product = product(productMartId, deleted, status, stock);

        // when
        CartItemStatus result = CartItemStatus.of(product, MART_ID, 1);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"7", "8"})
    @DisplayName("이용 마트가 없는 회원의 장바구니 상품은 모두 이전 마트 상품이다")
    void of_noMart(Long productMartId) {
        // given
        ProductSummary product = product(productMartId, false, ProductStatus.SELLING, 10);

        // when
        CartItemStatus result = CartItemStatus.of(product, null, 1);

        // then
        assertThat(result).isEqualTo(CartItemStatus.PREVIOUS_MART);
    }

    private ProductSummary product(Long martId, boolean deleted, ProductStatus status, int stock) {
        return new ProductSummary(10L, martId, "애호박", null, 1500L, null, null, status, stock, deleted);
    }
}
