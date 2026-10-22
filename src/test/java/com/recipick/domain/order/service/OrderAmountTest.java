package com.recipick.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderAmountTest {

    @ParameterizedTest(name = "상품금액 {0}원이면 배송비 {1}원, 결제금액 {2}원")
    @CsvSource({"39999, 3000, 42999", "40000, 0, 40000", "1000, 3000, 4000"})
    @DisplayName("상품금액 40,000원 이상이면 무료배송이고 미만이면 배송비 3,000원을 더한다")
    void of(long productAmount, long deliveryFee, long payAmount) {
        // when
        OrderAmount amount = OrderAmount.of(productAmount);

        // then
        assertThat(amount.productAmount()).isEqualTo(productAmount);
        assertThat(amount.deliveryFee()).isEqualTo(deliveryFee);
        assertThat(amount.payAmount()).isEqualTo(payAmount);
    }
}
