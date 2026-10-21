package com.recipick.domain.payment.facade;

import com.recipick.domain.order.service.OrderService;
import com.recipick.domain.payment.dto.response.PaymentConfirmResponse;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.service.PaymentApprovalOutcome;
import com.recipick.domain.payment.service.PaymentService;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.ErrorCode;
import com.recipick.infra.portone.PortOnePaymentClient;
import com.recipick.infra.portone.dto.PortOnePayment;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 결제 확정 API(1-1)의 진입점. 트랜잭션을 걸지 않는다 — 가운데 PortOne 조회가 있어서다.
 * 선검증 구간(각 Service의 짧은 readOnly 읽기) → PortOne 조회 → 상태 변경(Processor의 TX-2) 순이다.
 * 결제 상태에 따른 판정은 여기서 하지 않는다 — Verifier가 잠금 아래에서 한 번만 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentConfirmFacade {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final PortOnePaymentClient portOnePaymentClient;
    private final PaymentConfirmProcessor paymentConfirmProcessor;

    public PaymentConfirmResponse confirm(Long memberId, Long orderId, String portonePaymentId) {
        // 검증 1~3. 셋 다 404 PAYMENT-001로 답해 남의 주문인지·번호가 틀린지를 구분해 주지 않는다
        Payment payment = paymentService.getByOrderId(orderId);
        if (!orderService.getMemberIdOf(orderId).equals(memberId)) {
            log.warn("다른 회원의 주문에 결제 확정 시도: memberId={}, orderId={}", memberId, orderId);
            throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }
        if (!payment.getPortonePaymentId().equals(portonePaymentId)) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }

        // 프론트가 보낸 결과·금액은 믿지 않는다. 승인 판단은 PortOne에 직접 물은 결과로만 한다
        PortOnePayment portOnePayment = portOnePaymentClient.getPayment(portonePaymentId);

        PaymentApprovalOutcome outcome = paymentConfirmProcessor.process(orderId, portOnePayment);

        // 여기 도달했으면 TX-2는 이미 커밋됐다. 지금 예외를 던져도 FAILED·환불 행은 남는다
        Optional<ErrorCode> errorCode = outcome.errorCode();
        if (errorCode.isPresent()) {
            throw new BusinessException(errorCode.get());
        }

        // 위의 payment는 선검증 시점 스냅샷이라 PAID·paidAt이 없다. 반영된 행을 다시 읽어 돌려준다 (ALREADY_PAID도 같은 길)
        return PaymentConfirmResponse.from(paymentService.getByOrderId(orderId));
    }
}
