package com.recipick.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("이미 결제 완료된 주문에 결제 성공이 다시 오면 상태와 이력을 바꾸지 않는다")
    void markPaid_alreadyPaid() {
        // given
        LocalDateTime firstPaidAt = LocalDateTime.of(2026, 10, 20, 12, 0);
        Order order = Order.builder()
                .status(OrderStatus.PENDING_PAYMENT)
                .martStatus(MartStatus.NONE)
                .build();
        order.markPaid(firstPaidAt);
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        orderService.markPaid(1L, firstPaidAt.plusMinutes(1));

        // then
        assertThat(order.getPaidAt()).isEqualTo(firstPaidAt);
        verify(orderStatusHistoryRepository, never()).saveAll(any());
    }
}
