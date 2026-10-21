package com.recipick.domain.order.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.product.dto.StockQuantity;
import com.recipick.domain.product.service.ProductStockService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @Mock
    private OrderService orderService;

    @Mock
    private ProductStockService productStockService;

    @InjectMocks
    private OrderFacade orderFacade;

    @Test
    @DisplayName("결제 실패로 주문을 종료하면 재고는 건드리지 않고 복구할 상품 수량을 돌려준다")
    void closeByPaymentFailure() {
        // given
        given(orderService.closeByPaymentFailure(1L, CancelReasonCode.PAYMENT_FAILED)).willReturn(List.of(
                OrderItem.builder().productId(10L).quantity(2).build(),
                OrderItem.builder().productId(20L).quantity(1).build()));

        // when
        List<StockQuantity> result = orderFacade.closeByPaymentFailure(1L, CancelReasonCode.PAYMENT_FAILED);

        // then
        assertThat(result).containsExactly(new StockQuantity(10L, 2), new StockQuantity(20L, 1));
        verify(productStockService, never()).increaseStocks(any());
    }

    @Test
    @DisplayName("복구할 상품 수량만큼 재고를 복구한다")
    void restoreStock() {
        // given
        List<StockQuantity> items = List.of(new StockQuantity(10L, 2), new StockQuantity(20L, 1));

        // when
        orderFacade.restoreStock(items);

        // then
        verify(productStockService).increaseStocks(items);
    }

    @Test
    @DisplayName("이미 종료된 주문이라 복구할 상품이 없으면 재고를 복구하지 않는다")
    void restoreStock_empty() {
        // when
        orderFacade.restoreStock(List.of());

        // then
        verify(productStockService, never()).increaseStocks(any());
    }
}
