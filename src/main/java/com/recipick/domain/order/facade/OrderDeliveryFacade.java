package com.recipick.domain.order.facade;

import com.recipick.domain.member.entity.enums.AdminRole;
import com.recipick.domain.order.dto.response.OrderMartStatusResponse;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.service.PointCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 배송완료는 구매확정이라 포인트 적립 확정까지 한 트랜잭션으로 묶는다. 따로 커밋되면 배송완료인데 적립이 확정되지 않은
// 주문이 남는다. 주문 → 포인트 순으로 불러 잠금 순서 ORDERS → MEMBER_POINT → POINT_EARNING을 지킨다.
// 결제는 바뀌지 않으므로 PAYMENT를 잠그지 않는다 — 잠그면 결제 흐름(PAYMENT → ORDERS)과 순서가 뒤집힌다
@Component
@RequiredArgsConstructor
public class OrderDeliveryFacade {

    private final OrderService orderService;
    private final PointCommandService pointCommandService;

    @Transactional
    public OrderMartStatusResponse changeStatus(
        AdminRole role, Long adminMartId, Long adminId, Long orderId, MartStatus target) {
        OrderMartStatusResponse response =
            orderService.changeDeliveryStatus(role, adminMartId, adminId, orderId, target);

        if (target == MartStatus.DELIVERED) {
            pointCommandService.confirmEarning(orderId);
        }
        return response;
    }
}
