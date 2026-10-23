package com.recipick.domain.order.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.order.entity.OrderItem;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderNameFormatterTest {

    @Test
    @DisplayName("주문 상품이 하나면 상품명을 그대로 쓴다")
    void format_single() {
        // given
        List<OrderItem> items = items("애호박");

        // when
        String orderName = OrderNameFormatter.format(items);

        // then
        assertThat(orderName).isEqualTo("애호박");
    }

    @Test
    @DisplayName("주문 상품이 여러 개면 첫 상품명 외 나머지 개수로 쓴다")
    void format_multiple() {
        // given
        List<OrderItem> items = items("애호박", "두부", "대파");

        // when
        String orderName = OrderNameFormatter.format(items);

        // then
        assertThat(orderName).isEqualTo("애호박 외 2건");
    }

    private List<OrderItem> items(String... productNames) {
        return Arrays.stream(productNames)
                .map(name -> OrderItem.builder().productName(name).build())
                .toList();
    }
}
