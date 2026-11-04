package com.recipick.domain.product.entity;

import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

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

    @Test
    @DisplayName("재고 증가 : int 범위를 넘으면 INVALID_INPUT이고 재고는 그대로다")
    void increaseStocks_overflow() {
        // given
        Product product = Product.builder().name("애호박").totalQuantity(50).build();

        // when
        Throwable thrown = catchThrowable(() -> product.increaseStock(Integer.MAX_VALUE));

        // then
        assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.INVALID_INPUT);
        assertThat(product.getTotalQuantity()).isEqualTo(50);
    }

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 9, 0);

    private Product pricedProduct(long price) {
        return Product.builder().price(price).build();
    }

    @Test
    @DisplayName("판매가 : 할인 종료 전이면 정상가에서 할인율만큼 내린 값이다")
    void getSalePrice_discounted() {
        // given
        Product product = pricedProduct(10000L);
        product.changeDiscount(30, NOW.plusDays(1), NOW);

        // when
        Long salePrice = product.getSalePrice(NOW);

        // then
        assertThat(salePrice).isEqualTo(7000L);
        assertThat(product.isDiscounted(NOW)).isTrue();
        assertThat(product.getPrice()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("판매가 : 원 단위 미만은 내림한다")
    void getSalePrice_roundDown() {
        // given
        Product product = pricedProduct(9999L);
        product.changeDiscount(10, NOW.plusDays(1), NOW);

        // when
        Long salePrice = product.getSalePrice(NOW);

        // then
        assertThat(salePrice).isEqualTo(8999L);
    }

    @Test
    @DisplayName("판매가 : 종료 시각이 되면 정상가로 돌아온다")
    void getSalePrice_endedAtBoundary() {
        // given
        Product product = pricedProduct(10000L);
        LocalDateTime endAt = NOW.plusHours(1);
        product.changeDiscount(30, endAt, NOW);

        // when
        Long beforeEnd = product.getSalePrice(endAt.minusSeconds(1));
        Long atEnd = product.getSalePrice(endAt);

        // then
        assertThat(beforeEnd).isEqualTo(7000L);
        assertThat(atEnd).isEqualTo(10000L);
        assertThat(product.isDiscounted(endAt)).isFalse();
    }

    @Test
    @DisplayName("할인 지정 : 할인율이 1~99를 벗어나면 INVALID_DISCOUNT_RATE이고 값은 그대로다")
    void changeDiscount_invalidRate() {
        // given
        Product product = pricedProduct(10000L);

        // when
        Throwable zero = catchThrowable(() -> product.changeDiscount(0, NOW.plusDays(1), NOW));
        Throwable hundred = catchThrowable(() -> product.changeDiscount(100, NOW.plusDays(1), NOW));

        // then
        assertThat(zero).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.INVALID_DISCOUNT_RATE);
        assertThat(hundred).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.INVALID_DISCOUNT_RATE);
        assertThat(product.getDiscountRate()).isZero();
    }

    @Test
    @DisplayName("할인 지정 : 종료 시각이 현재 이전이거나 같으면 INVALID_DISCOUNT_END_AT이다")
    void changeDiscount_invalidEndAt() {
        // given
        Product product = pricedProduct(10000L);

        // when
        Throwable past = catchThrowable(() -> product.changeDiscount(10, NOW.minusSeconds(1), NOW));
        Throwable same = catchThrowable(() -> product.changeDiscount(10, NOW, NOW));

        // then
        assertThat(past).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.INVALID_DISCOUNT_END_AT);
        assertThat(same).isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.INVALID_DISCOUNT_END_AT);
        assertThat(product.getDiscountEndAt()).isNull();
    }

    @Test
    @DisplayName("할인 해제 : 할인율과 종료 시각이 지워지고 정상가가 판매가다")
    void clearDiscount() {
        // given
        Product product = pricedProduct(10000L);
        product.changeDiscount(30, NOW.plusDays(1), NOW);

        // when
        product.clearDiscount();

        // then
        assertThat(product.getDiscountRate()).isZero();
        assertThat(product.getDiscountEndAt()).isNull();
        assertThat(product.getSalePrice(NOW)).isEqualTo(10000L);
    }
}
