package com.recipick.domain.payment.facade;

import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.PaymentCreated;
import com.recipick.domain.payment.service.PaymentCommandService;
import com.recipick.domain.payment.service.PointCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 외부 결제 금액이 0원인 주문을 주문 생성 트랜잭션 안에서 결제 완료로 닫는다.
// PG가 없으니 결제 확정 API·웹훅·만료 큐를 거치지 않고, 카드 결제 성공(PaymentConfirmProcessor)과 같은 순서로 결제 PAID → 주문 PAID → 사용 포인트 확정을 한 번에 반영한다.
@Component
@RequiredArgsConstructor
public class PointsOnlyPaymentProcessor {

    private final PaymentCommandService paymentCommandService;
    private final OrderService orderService;
    private final PointCommandService pointCommandService;

    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentCreated complete(Long orderId) {
        PaymentCreated payment = paymentCommandService.createPointsOnly(orderId);
        // 주문 결제 시각을 결제 완료 시각과 같은 값으로 맞춘다
        orderService.markPaid(orderId, payment.paidAt());
        pointCommandService.confirmUsage(orderId);
        return payment;
    }
}
