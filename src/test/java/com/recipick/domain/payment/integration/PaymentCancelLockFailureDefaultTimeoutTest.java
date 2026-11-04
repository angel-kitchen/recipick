package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.facade.PaymentCancelFacade;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// PaymentCancelLockFailureTest는 잠금 대기 한도를 1초로 줄여 돌린다. 운영·CI와 같은 기본 한도(5초)에서도
// 같은 예외가 같은 롤백·응답 경로로 이어지는지 한 건만 확인한다. 두 회차를 기다리므로 약 10초 걸린다
class PaymentCancelLockFailureDefaultTimeoutTest extends PaymentFlowIntegrationSupport {

    @Autowired
    PaymentCancelFacade paymentCancelFacade;

    @Test
    @DisplayName("기본 잠금 대기 한도에서도 TX1이 두 회차 모두 잠금을 못 얻으면 COMMON-010이고 PortOne을 부르지 않으며 아무것도 바뀌지 않는다")
    void tx1LockFailureExhaustedWithDefaultTimeout() throws Exception {
        // given
        Flow flow = paidPaymentWithStock();
        BusinessException thrown;
        try (RowLockHolder holder = new RowLockHolder(transactionTemplate)) {
            holder.hold(() -> jdbcTemplate.queryForObject(
                "SELECT id FROM payment WHERE id = ? FOR UPDATE", Long.class, flow.paymentId()));

            // when
            thrown = catchThrowableOfType(BusinessException.class, () -> paymentCancelFacade.cancel(
                flow.memberId(), flow.paymentId(), "lock-default-" + flow.orderId(), CANCEL_REASON));
        }

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(CommonErrorCode.LOCK_ACQUISITION_TIMEOUT);
        verify(portOnePaymentClient, never()).cancelPayment(anyString(), anyString(), anyString(), anyLong());
        assertThat(refundsOf(flow)).isEmpty();
        assertThat(paymentOf(flow).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(orderOf(flow).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(orderStatusHistoryRepository.findAllByOrderId(flow.orderId())).isEmpty();
        assertThat(stockOf(flow)).isEqualTo(STOCK_AFTER_ORDER);
    }
}
