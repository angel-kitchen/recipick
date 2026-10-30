package com.recipick.domain.order.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.order.dto.response.OrderMartStatusResponse;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.service.PointCommandService;
import com.recipick.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderDeliveryFacadeTest {

    private static final Long ORDER_ID = 1L;
    private static final Long MART_ID = 3L;
    private static final Long ADMIN_ID = 20L;

    @Mock
    private OrderService orderService;

    @Mock
    private PointCommandService pointCommandService;

    @InjectMocks
    private OrderDeliveryFacade orderDeliveryFacade;

    @Test
    @DisplayName("배송중으로 바꾸면 주문만 바꾸고 포인트는 건드리지 않는다")
    void changeStatus_shipping() {
        // given
        OrderMartStatusResponse response = response(MartStatus.SHIPPING);
        given(orderService.changeDeliveryStatus(AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID,
            MartStatus.SHIPPING)).willReturn(response);

        // when
        OrderMartStatusResponse result = orderDeliveryFacade.changeStatus(
            AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID, MartStatus.SHIPPING);

        // then
        assertThat(result).isEqualTo(response);
        verifyNoInteractions(pointCommandService);
    }

    @Test
    @DisplayName("배송완료면 주문을 먼저 바꾼 뒤 포인트 적립을 확정한다")
    void changeStatus_delivered() {
        // given
        OrderMartStatusResponse response = response(MartStatus.DELIVERED);
        given(orderService.changeDeliveryStatus(AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID,
            MartStatus.DELIVERED)).willReturn(response);

        // when
        OrderMartStatusResponse result = orderDeliveryFacade.changeStatus(
            AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID, MartStatus.DELIVERED);

        // then
        assertThat(result).isEqualTo(response);
        InOrder inOrder = inOrder(orderService, pointCommandService);
        inOrder.verify(orderService).changeDeliveryStatus(
            AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID, MartStatus.DELIVERED);
        inOrder.verify(pointCommandService).confirmEarning(ORDER_ID);
    }

    @Test
    @DisplayName("이미 배송완료인 주문에 배송완료가 다시 와도 적립 확정을 한 번 부른다 — 중복 지급은 포인트 쪽이 막는다")
    void changeStatus_deliveredAgain() {
        // given: 주문 쪽은 같은 요청 반복이라 현재 상태만 돌려준다
        given(orderService.changeDeliveryStatus(AdminRole.SUPER_ADMIN, null, ADMIN_ID, ORDER_ID,
            MartStatus.DELIVERED)).willReturn(response(MartStatus.DELIVERED));

        // when
        orderDeliveryFacade.changeStatus(AdminRole.SUPER_ADMIN, null, ADMIN_ID, ORDER_ID, MartStatus.DELIVERED);

        // then
        verify(pointCommandService).confirmEarning(ORDER_ID);
    }

    @Test
    @DisplayName("포인트 적립 확정이 실패하면 같은 예외를 그대로 던져 바깥 트랜잭션이 주문 변경까지 롤백하게 한다")
    void changeStatus_pointFailure() {
        // given
        given(orderService.changeDeliveryStatus(AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID,
            MartStatus.DELIVERED)).willReturn(response(MartStatus.DELIVERED));
        IllegalStateException failure = new IllegalStateException("적립 확정을 할 수 없는 적립 상태입니다.");
        willThrow(failure).given(pointCommandService).confirmEarning(ORDER_ID);

        // when, then
        assertThatThrownBy(() -> orderDeliveryFacade.changeStatus(
            AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID, MartStatus.DELIVERED))
            .isSameAs(failure);
    }

    @Test
    @DisplayName("주문 쪽에서 거절되면 포인트 적립 확정을 부르지 않는다")
    void changeStatus_orderRejected() {
        // given
        given(orderService.changeDeliveryStatus(AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID,
            MartStatus.DELIVERED))
            .willThrow(new BusinessException(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION));

        // when, then
        assertThatThrownBy(() -> orderDeliveryFacade.changeStatus(
            AdminRole.MART_ADMIN, MART_ID, ADMIN_ID, ORDER_ID, MartStatus.DELIVERED))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        verify(pointCommandService, never()).confirmEarning(any());
    }

    private OrderMartStatusResponse response(MartStatus martStatus) {
        return new OrderMartStatusResponse(ORDER_ID, OrderStatus.PAID, martStatus);
    }
}
