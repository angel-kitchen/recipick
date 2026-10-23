package com.recipick.domain.payment.facade;

import com.recipick.domain.order.entity.enums.CancelReasonCode;
import com.recipick.domain.order.facade.OrderFacade;
import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.RefundSummary;
import com.recipick.domain.payment.dto.response.PaymentCancelResponse;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.enums.PaymentFailureReason;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.domain.product.dto.StockQuantity;
import com.recipick.global.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCancelProcessor {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final OrderFacade orderFacade;

    @Transactional
    public PaymentCancelResponse cancelBeforePay(Long memberId, Long paymentId, String reason) {
        Payment payment = paymentService.getByIdForUpdate(paymentId);
        Long orderId = payment.getOrderId();

        // 멱등 판정보다 먼저 본다. 순서가 바뀌면 남의 결제가 이미 취소됐는지를 200/404 차이로 알 수 있게 된다
        if (!orderService.getMemberIdOf(orderId).equals(memberId)) {
            log.warn("다른 회원의 결제에 취소 시도: memberId={}, paymentId={}", memberId, paymentId);
            throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }

        // 결제 전 취소는 키를 저장하지 않는다. 이미 닫힌 상태 자체가 같은 요청이 처리됐다는 증거다
        if (isCanceledBeforePay(payment)) {
            return PaymentCancelResponse.of(payment, RefundSummary.NONE);
        }
        // PAID는 PG 취소가 필요해 후속 이슈에서 다룬다
        if (payment.getStatus() != PaymentStatus.IN_PROGRESS) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }

        payment.fail(PaymentFailureReason.CANCELED_BEFORE_PAY, null);
        // 락 순서 PAYMENT → ORDERS → (2차: MEMBER_POINT → POINT_EARNING) → 재고. 주문 종료와 재고 복구 사이가 포인트 자리다
        List<StockQuantity> items = orderFacade.closeByPaymentFailure(orderId, CancelReasonCode.MEMBER_CANCELED, reason);
        orderFacade.restoreStock(items);

        return PaymentCancelResponse.of(payment, RefundSummary.NONE);
    }

    private boolean isCanceledBeforePay(Payment payment) {
        return payment.getStatus() == PaymentStatus.FAILED
            && payment.getFailureReason() == PaymentFailureReason.CANCELED_BEFORE_PAY;
    }
}
