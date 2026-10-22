package com.recipick.domain.payment.facade;

import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.response.PaymentDetailResponse;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 결제 조회 api(1-2)의 진입점. 상태를 바꾸지 않으므로 잠금도 트랜잭션도 걸지 않는다.
// 도메인 Service끼리는 주입하지 않으므로 결제·주문 Service 조합은 여기서 한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentQueryFacade {

    private final PaymentService paymentService;
    private final OrderService orderService;

    public PaymentDetailResponse getByOrderId(Long memberId, Long orderId) {
        // 결제가 있으면 주문도 반드시 있다(주문 생성 트랜잭션에서 함께 만들어진다)
        Payment payment = paymentService.getByOrderId(orderId);
        if (!orderService.getMemberIdOf(orderId).equals(memberId)) {
            // 403이면 "그 주문은 있다"를 알려주는 셈이라 404로 답한다
            log.warn("다른 회원의 주문에 결제 조회 시도: memberId={}, orderId={}", memberId, orderId);
            throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }
        return PaymentDetailResponse.from(payment);
    }
}
