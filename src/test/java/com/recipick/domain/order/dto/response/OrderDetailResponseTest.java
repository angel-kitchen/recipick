package com.recipick.domain.order.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

    @Test
    @DisplayName("회원·관리자 주문 상세는 사용 포인트를 함께 내려 결제금액 차이를 설명한다")
    void usedPoint() {
        // given
        Order order = Order.builder().status(OrderStatus.PAID).martStatus(MartStatus.RECEIVED)
                .productAmount(5_000L).deliveryFee(3_000L).usedPoint(3_000L).payAmount(5_000L).build();
        List<OrderItem> items = List.of(OrderItem.builder().order(order).productName("애호박").build());

        // when
        OrderDetailResponse member = OrderDetailResponse.of(order, items);
        OrderAdminDetailResponse admin = OrderAdminDetailResponse.of(order, items, List.of());

        // then
        assertThat(member.usedPoint()).isEqualTo(3_000L);
        assertThat(admin.usedPoint()).isEqualTo(3_000L);
        assertThat(member.productAmount() + member.deliveryFee() - member.usedPoint()).isEqualTo(member.payAmount());
    }
}
