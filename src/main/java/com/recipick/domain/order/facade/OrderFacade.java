package com.recipick.domain.order.facade;

import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.product.dto.StockQuantity;
import com.recipick.domain.product.service.ProductStockService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;
    private final ProductStockService productStockService;

    // 락 순서 PAYMENT → ORDERS → MEMBER_POINT → POINT_EARNING → 재고에서 포인트 복구가 사이에 들어가야 해서
    // 주문 종료와 재고 복구를 나눈다. 결제 Facade가 한 트랜잭션 안에서 종료 → 포인트 복구 → 재고 복구 순으로 호출한다.
    // 따로 커밋되면 종료만 반영되고 재고 복구가 빠질 수 있어 바깥 트랜잭션 없이는 호출할 수 없게 한다.
    @Transactional(propagation = Propagation.MANDATORY)
    public List<StockQuantity> closeByPaymentFailure(
            Long orderId, CancelReasonCode reasonCode, String reasonDetail) {
        return toStockQuantities(orderService.closeByPaymentFailure(orderId, reasonCode, reasonDetail));
    }

    // 환불 완료도 포인트 복구가 주문과 재고 사이에 들어가야 해서 같은 이유로 재고 복구를 나눈다
    @Transactional(propagation = Propagation.MANDATORY)
    public List<StockQuantity> markRefunded(Long orderId) {
        return toStockQuantities(orderService.markRefunded(orderId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreStock(List<StockQuantity> items) {
        // 이미 종료된 주문은 복구할 재고가 없다
        if (items.isEmpty()) {
            return;
        }
        productStockService.increaseStocks(items);
    }

    private List<StockQuantity> toStockQuantities(List<OrderItem> items) {
        return items.stream()
                .map(item -> new StockQuantity(item.getProductId(), item.getQuantity()))
                .toList();
    }
}
