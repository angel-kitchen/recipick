package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.dto.response.PaymentCancelResponse;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// 결제 취소 API의 진입점. 트랜잭션을 걸지 않는다. 결제 후 취소가 붙으면 PortOne 호출이 이 자리에 들어간다.
// 결제 전 취소는 외부 호출이 없어 Processor의 트랜잭션 하나로 끝난다.
@Component
@RequiredArgsConstructor
public class PaymentCancelFacade {

    private final PaymentCancelProcessor paymentCancelProcessor;

    public PaymentCancelResponse cancel(Long memberId, Long paymentId, String idempotencyKey, String reason) {
        // 헤더가 아예 없으면 GlobalExceptionHandler가 COMMON-007로 바꾼다. 여기서는 빈 값으로 온 경우를 막는다.
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(CommonErrorCode.MISSING_IDEMPOTENCY_KEY);
        }
        return paymentCancelProcessor.cancelBeforePay(memberId, paymentId, reason);
    }
}
