package com.recipick.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderAmountTest {

    @ParameterizedTest(name = "상품금액 {0}원이면 배송비 {1}원, 결제금액 {2}원")
    @CsvSource({"39999, 3000, 42999", "40000, 0, 40000", "1000, 3000, 4000"})
    @DisplayName("상품금액 40,000원 이상이면 무료배송이고 미만이면 배송비 3,000원을 더한다")
    void of(long productAmount, long deliveryFee, long payAmount) {
        // when
        OrderAmount amount = OrderAmount.of(productAmount, false);

        // then
        assertThat(amount.productAmount()).isEqualTo(productAmount);
        assertThat(amount.deliveryFee()).isEqualTo(deliveryFee);
        assertThat(amount.payAmount()).isEqualTo(payAmount);
    }

    @ParameterizedTest(name = "멤버십 회원 상품금액 {0}원·사용 포인트 {1}P면 배송비 {2}원, 결제금액 {3}원")
    @CsvSource({
        "14999, 0, 3000, 17999",
        "15000, 0, 0, 15000",
        "15000, 5000, 0, 10000",
        "39999, 0, 0, 39999"
    })
    @DisplayName("멤버십 회원은 포인트 사용 전 상품금액 15,000원 이상이면 무료배송이고 미만이면 배송비 3,000원을 더한다")
    void ofWithMembership(long productAmount, long usedPoint, long deliveryFee, long payAmount) {
        // when
        OrderAmount amount = OrderAmount.of(productAmount, usedPoint, true);

        // then
        assertThat(amount.deliveryFee()).isEqualTo(deliveryFee);
        assertThat(amount.payAmount()).isEqualTo(payAmount);
    }

    @ParameterizedTest(name = "상품금액 {0}원·사용 포인트 {1}P면 배송비 {2}원, 결제금액 {3}원")
    @CsvSource({
        "20000, 0, 3000, 23000",
        "20000, 5000, 3000, 18000",
        "20000, 20000, 3000, 3000",
        "40000, 1000, 0, 39000",
        "50000, 50000, 0, 0"
    })
    @DisplayName("결제금액은 상품금액 + 배송비 − 사용 포인트이고, 배송비는 포인트 사용 전 상품금액으로 정해 포인트로 내지 않는다")
    void ofWithUsedPoint(long productAmount, long usedPoint, long deliveryFee, long payAmount) {
        // when
        OrderAmount amount = OrderAmount.of(productAmount, usedPoint, false);

        // then
        assertThat(amount.productAmount()).isEqualTo(productAmount);
        assertThat(amount.deliveryFee()).isEqualTo(deliveryFee);
        assertThat(amount.payAmount()).isEqualTo(payAmount);
    }

    @Test
    @DisplayName("사용 포인트가 상품금액을 넘으면 배송비가 남아 있어도 ORDER-019로 거절한다")
    void rejectUsedPointOverProductAmount() {
        // when, then
        assertThatThrownBy(() -> OrderAmount.of(5_000L, 5_001L, false))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(OrderErrorCode.USED_POINT_EXCEEDS_PRODUCT_AMOUNT);
    }

    @Test
    @DisplayName("음수 사용 포인트는 요청 검증을 지난 뒤에는 나올 수 없는 값이라 호출자 버그로 막는다")
    void rejectNegativeUsedPoint() {
        // when, then
        assertThatThrownBy(() -> OrderAmount.of(5_000L, -1L, false))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
