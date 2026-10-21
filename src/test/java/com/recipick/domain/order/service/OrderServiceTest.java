package com.recipick.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderStatusHistory;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.entity.enums.OrderStatusActor;
import com.recipick.domain.order.entity.enums.OrderStatusType;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
import com.recipick.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 10, 20, 12, 0);

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @InjectMocks
    private OrderService orderService;

    @Captor
    private ArgumentCaptor<List<OrderStatusHistory>> historiesCaptor;

    private Order pendingOrder() {
        return Order.builder()
                .memberId(7L)
                .status(OrderStatus.PENDING_PAYMENT)
                .martStatus(MartStatus.NONE)
                .build();
    }

    @Test
    @DisplayName("주문의 회원 ID를 돌려준다")
    void getMemberIdOf() {
        // given
        given(orderRepository.findById(1L)).willReturn(Optional.of(pendingOrder()));

        // when
        Long memberId = orderService.getMemberIdOf(1L);

        // then
        assertThat(memberId).isEqualTo(7L);
    }

    @Test
    @DisplayName("없는 주문의 회원 ID를 조회하면 주문을 찾을 수 없다는 오류가 난다")
    void getMemberIdOf_orderNotFound() {
        // given
        given(orderRepository.findById(1L)).willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> orderService.getMemberIdOf(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("결제 대기 주문에 결제 성공이 오면 주문 상태와 마트 처리 상태 이력을 시스템 주체로 저장한다")
    void markPaid() {
        // given
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(pendingOrder()));

        // when
        orderService.markPaid(1L, PAID_AT);

        // then
        verify(orderStatusHistoryRepository).saveAll(historiesCaptor.capture());
        assertThat(historiesCaptor.getValue())
                .extracting(OrderStatusHistory::getStatusType, OrderStatusHistory::getFromStatus,
                        OrderStatusHistory::getToStatus, OrderStatusHistory::getActor)
                .containsExactly(
                        tuple(OrderStatusType.ORDER, "PENDING_PAYMENT", "PAID", OrderStatusActor.SYSTEM),
                        tuple(OrderStatusType.MART, "NONE", "RECEIVED", OrderStatusActor.SYSTEM));
    }

    @Test
    @DisplayName("없는 주문에 결제 성공이 오면 주문을 찾을 수 없다는 오류가 난다")
    void markPaid_orderNotFound() {
        // given
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> orderService.markPaid(1L, PAID_AT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
        verify(orderStatusHistoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("이미 결제 완료된 주문에 결제 성공이 다시 오면 상태와 이력을 바꾸지 않는다")
    void markPaid_alreadyPaid() {
        // given
        Order order = pendingOrder();
        order.markPaid(PAID_AT);
        given(orderRepository.findByIdForUpdate(1L)).willReturn(Optional.of(order));

        // when
        orderService.markPaid(1L, PAID_AT.plusMinutes(1));

        // then
        assertThat(order.getPaidAt()).isEqualTo(PAID_AT);
        verify(orderStatusHistoryRepository, never()).saveAll(any());
    }
}
