package com.recipick.domain.order.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderDetailResponseTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING_PAYMENT, NONE, true",
            "PAID, RECEIVED, true",
            "PAID, PREPARING, false",
            "PAID, SHIPPING, false",
            "PAID, DELIVERED, false",
            "CANCEL_REQUESTED, RECEIVED, false",
            "CANCEL_REQUESTED, REJECTED, false",
            "CANCELED, NONE, false",
            "REFUNDED, RECEIVED, false"
    })
    @DisplayName("결제 대기이거나 결제 완료·마트 접수중이면 취소할 수 있다")
    void cancelable(OrderStatus status, MartStatus martStatus, boolean expected) {
        // given
        Order order = Order.builder().status(status).martStatus(martStatus).build();
        List<OrderItem> items = List.of(OrderItem.builder().order(order).productName("애호박").build());

        // when
        OrderDetailResponse response = OrderDetailResponse.of(order, items);

        // then
        assertThat(response.cancelable()).isEqualTo(expected);
    }
}
