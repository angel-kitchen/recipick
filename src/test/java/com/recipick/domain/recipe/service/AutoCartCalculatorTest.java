package com.recipick.domain.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.domain.recipe.entity.UnavailableReason;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.global.common.SaleUnit;
import com.recipick.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutoCartCalculatorTest {

    private static final Long TOFU_ID = 3L;

    private RequiredIngredient tofu(String totalRequiredAmount) {
        return new RequiredIngredient(TOFU_ID, "두부", SaleUnit.G, new BigDecimal(totalRequiredAmount));
    }

    private ProductSummary product(Long id, String name, String saleUnitAmount, long price, int stock) {
        return product(id, name, saleUnitAmount, price, stock, ProductStatus.SELLING);
    }

    private ProductSummary product(
        Long id, String name, String saleUnitAmount, long price, int stock, ProductStatus status) {
        return new ProductSummary(id, 1L, name, null, price, new BigDecimal(saleUnitAmount), SaleUnit.G,
            status, stock, false);
    }

    private AutoCartLine calculateOne(RequiredIngredient ingredient, List<ProductSummary> candidates,
                                      Map<Long, Integer> cartQuantities) {
        return AutoCartCalculator.calculate(
            List.of(ingredient), Map.of(ingredient.ingredientId(), candidates), cartQuantities, Map.of()).get(0);
    }

    @Test
    @DisplayName("합산한 필요량을 판매 단위로 한 번만 올림하고 예상 잔량을 계산한다")
    void calculate_ceilingOnce() {
        // given
        ProductSummary tofu300 = product(1001L, "국산콩 두부 300g", "300", 2500L, 10);

        // when
        AutoCartLine line = calculateOne(tofu("500"), List.of(tofu300), Map.of());

        // then
        assertThat(line.purchasable()).isTrue();
        assertThat(line.purchaseQuantity()).isEqualTo(2);
        assertThat(line.linePrice()).isEqualTo(5000L);
        assertThat(line.selected().expectedRemainingAmount()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("소수 필요량도 오차 없이 나누어 떨어지면 더 올림하지 않는다")
    void calculate_decimalWithoutError() {
        // given
        ProductSummary half = product(1001L, "애호박 반개", "0.1", 1000L, 10);

        // when
        AutoCartLine line = calculateOne(tofu("0.3"), List.of(half), Map.of());

        // then
        assertThat(line.purchaseQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("구매 가능한 상품 중 총 구매금액이 낮은 상품을 고른다")
    void calculate_cheapestLinePrice() {
        // given
        ProductSummary tofu300 = product(1001L, "국산콩 두부 300g", "300", 2500L, 10);
        ProductSummary tofu500 = product(1002L, "부침용 두부 500g", "500", 4200L, 10);

        // when
        AutoCartLine line = calculateOne(tofu("500"), List.of(tofu300, tofu500), Map.of());

        // then
        assertThat(line.selected().product().productId()).isEqualTo(1002L);
        assertThat(line.candidates())
            .extracting(option -> option.product().productId())
            .containsExactly(1002L, 1001L);
    }

    @Test
    @DisplayName("금액이 같으면 예상 잔량이 적은 상품, 그것도 같으면 상품명 가나다순으로 고른다")
    void calculate_tieBreak() {
        // given
        ProductSummary bigger = product(1001L, "가 두부 400g", "400", 3000L, 10);
        ProductSummary nameB = product(1002L, "나 두부 300g", "300", 3000L, 10);
        ProductSummary nameA = product(1003L, "다 두부 300g", "300", 3000L, 10);

        // when
        AutoCartLine line = calculateOne(tofu("300"), List.of(bigger, nameA, nameB), Map.of());

        // then
        assertThat(line.candidates())
            .extracting(option -> option.product().productId())
            .containsExactly(1002L, 1003L, 1001L);
    }

    @Test
    @DisplayName("더 싸더라도 구매할 수 없는 상품보다 구매 가능한 상품을 먼저 고른다")
    void calculate_purchasableFirst() {
        // given
        ProductSummary stopped = product(1001L, "할인 두부", "300", 1000L, 10, ProductStatus.STOPPED);
        ProductSummary selling = product(1002L, "일반 두부", "300", 2500L, 10);

        // when
        AutoCartLine line = calculateOne(tofu("300"), List.of(stopped, selling), Map.of());

        // then
        assertThat(line.selected().product().productId()).isEqualTo(1002L);
    }

    @Test
    @DisplayName("연결 상품이 없으면 이용 마트 미판매로 표시하고 금액에 넣지 않는다")
    void calculate_notSoldInMart() {
        // when
        AutoCartLine line = calculateOne(tofu("300"), List.of(), Map.of());

        // then
        assertThat(line.purchasable()).isFalse();
        assertThat(line.unavailableReason()).isEqualTo(UnavailableReason.NOT_SOLD_IN_MART);
        assertThat(line.linePrice()).isZero();
    }

    @Test
    @DisplayName("판매 중지·품절 상품만 있으면 그 사유로 구매 불가 처리한다")
    void calculate_stoppedAndSoldOut() {
        // given
        ProductSummary stopped = product(1001L, "두부", "300", 2500L, 10, ProductStatus.STOPPED);
        ProductSummary soldOut = product(1002L, "두부", "300", 2500L, 0);

        // when
        AutoCartLine stoppedLine = calculateOne(tofu("300"), List.of(stopped), Map.of());
        AutoCartLine soldOutLine = calculateOne(tofu("300"), List.of(soldOut), Map.of());

        // then
        assertThat(stoppedLine.unavailableReason()).isEqualTo(UnavailableReason.SALE_STOPPED);
        assertThat(soldOutLine.unavailableReason()).isEqualTo(UnavailableReason.SOLD_OUT);
    }

    @Test
    @DisplayName("장바구니에 담긴 수량과 이번 구매 수량을 더한 값이 재고보다 많으면 재고 부족이다")
    void calculate_insufficientStockWithCart() {
        // given
        ProductSummary tofu300 = product(1001L, "두부", "300", 2500L, 3);

        // when
        AutoCartLine line = calculateOne(tofu("600"), List.of(tofu300), Map.of(1001L, 2));

        // then
        assertThat(line.unavailableReason()).isEqualTo(UnavailableReason.INSUFFICIENT_STOCK);
        assertThat(line.purchaseQuantity()).isZero();
    }

    @Test
    @DisplayName("사용자가 고른 후보 상품이 있으면 기본 선택 대신 그 상품을 쓴다")
    void calculate_productSelection() {
        // given
        ProductSummary tofu300 = product(1001L, "국산콩 두부 300g", "300", 2500L, 10);
        ProductSummary tofu500 = product(1002L, "부침용 두부 500g", "500", 4200L, 10);

        // when
        AutoCartLine line = AutoCartCalculator.calculate(List.of(tofu("500")),
            Map.of(TOFU_ID, List.of(tofu300, tofu500)), Map.of(), Map.of(TOFU_ID, 1001L)).get(0);

        // then
        assertThat(line.selected().product().productId()).isEqualTo(1001L);
        assertThat(line.linePrice()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("후보에 없는 상품을 고르면 INVALID_PRODUCT_SELECTION 예외를 던진다")
    void calculate_invalidSelection() {
        // given
        ProductSummary tofu300 = product(1001L, "국산콩 두부 300g", "300", 2500L, 10);

        // when
        Throwable thrown = catchThrowable(() -> AutoCartCalculator.calculate(List.of(tofu("500")),
            Map.of(TOFU_ID, List.of(tofu300)), Map.of(), Map.of(TOFU_ID, 9999L)));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(RecipeErrorCode.INVALID_PRODUCT_SELECTION));
    }

    @Test
    @DisplayName("요청에 없는 재료의 상품을 고르면 INVALID_PRODUCT_SELECTION 예외를 던진다")
    void calculate_selectionForUnknownIngredient() {
        // when
        Throwable thrown = catchThrowable(() -> AutoCartCalculator.calculate(List.of(tofu("500")),
            Map.of(), Map.of(), Map.of(99L, 1001L)));

        // then
        assertThat(thrown)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(RecipeErrorCode.INVALID_PRODUCT_SELECTION));
    }
}
