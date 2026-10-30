package com.recipick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.PaymentCreated;
import com.recipick.domain.payment.service.PaymentCommandService;
import com.recipick.domain.payment.service.PointCommandService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PointsOnlyPaymentProcessorTest {

    private static final Long ORDER_ID = 1L;
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 10, 29, 14, 0);

    @Mock
    private PaymentCommandService paymentCommandService;

    @Mock
    private OrderService orderService;

    @Mock
    private PointCommandService pointCommandService;

    @InjectMocks
    private PointsOnlyPaymentProcessor processor;

    @Test
    @DisplayName("0원 결제는 결제 완료 생성 → 주문 결제 완료(같은 결제 시각) → 사용 포인트 확정 순으로 한 번에 반영하고 카드 결제는 만들지 않는다")
    void complete() {
        // given
        PaymentCreated created = new PaymentCreated(10L, null, 0L, null, PAID_AT);
        given(paymentCommandService.createPointsOnly(ORDER_ID)).willReturn(created);

        // when
        PaymentCreated result = processor.complete(ORDER_ID);

        // then
        assertThat(result).isEqualTo(created);
        InOrder inOrder = inOrder(paymentCommandService, orderService, pointCommandService);
        inOrder.verify(paymentCommandService).createPointsOnly(ORDER_ID);
        inOrder.verify(orderService).markPaid(ORDER_ID, PAID_AT);
        inOrder.verify(pointCommandService).confirmUsage(ORDER_ID);
        verify(paymentCommandService, never()).create(anyLong(), anyLong());
    }
}
