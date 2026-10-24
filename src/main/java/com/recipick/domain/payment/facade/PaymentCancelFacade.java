package com.recipick.domain.payment.facade;

import com.recipick.domain.payment.dto.PaymentCancelPreparation;
import com.recipick.domain.payment.dto.PaymentCancelResult;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.global.exception.ErrorCode;
import com.recipick.infra.portone.PortOnePaymentClient;
import com.recipick.infra.portone.dto.PortOneCancelResult;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// 결제 취소 API의 진입점. 트랜잭션을 걸지 않는다. ㅡ 가운데 PortOne 호출이 있기 때문
// TX1(접수) -> PortOne 전액 취소 -> TX2(반영) 순이고, 결제 전 취소는 TX1에서 끝난다
// 응답 본문은 Processor가 트랜잭션 안에서 만든 것을 그대로 쓴다. 실패 outcome의 예외는 TX2가 커밋된 뒤 여기서 던진다 —
// Processor 안에서 던지면 PAID 복귀·REFUND FAILED가 롤백된다.
@Component
@RequiredArgsConstructor
public class PaymentCancelFacade {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    // // PortOne에는 클라이언트 키 대신 환불 행마다 고정된 서버 키를 보낸다. PortOne의 길이·문자 제약이 우리 API로 새지 않는다
    private static final String PORTONE_IDEMPOTENCY_KEY_PREFIX = "recipick-refund-";

    private final PaymentCancelProcessor paymentCancelProcessor;
    private final PortOnePaymentClient portOnePaymentClient;

    public PaymentCancelResult cancel(Long memberId, Long paymentId, String idempotencyKey, String reason) {
        validateIdempotencyKey(idempotencyKey);

        PaymentCancelPreparation preparation =
            paymentCancelProcessor.prepare(memberId, paymentId, idempotencyKey, reason);
        PaymentCancelResult result = preparation.finishedResult();

        if (preparation.requiresPortOneCall()) {
            // TX1은 커밋되고 잠금은 풀렸다. 여기서 PortOne을 기다려도 같은 결제의 다른 요청이 락에 묶이지 않는다
            PortOneCancelResult portOneResult = portOnePaymentClient.cancelPayment(
                preparation.portonePaymentId(),
                PORTONE_IDEMPOTENCY_KEY_PREFIX + preparation.refundId(),
                reason,
                preparation.amount());
            result = paymentCancelProcessor.apply(paymentId, preparation.refundId(), portOneResult);
        }

        // 여기 도달했으면 TX2는 이미 커밋됐다. 지금 던져도 PAID 복귀·REFUND FAILED는 남는다
        Optional<ErrorCode> errorCode = result.outcome().errorCode();
        if (errorCode.isPresent()) {
            throw new BusinessException(errorCode.get());
        }
        return result;
    }

    // 헤더가 아예 없으면 GlobalExceptionHandler가 COMMON-007로 바꾼다. 여기서는 빈 값과 길이를 막는다
    private void validateIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(CommonErrorCode.MISSING_IDEMPOTENCY_KEY);
        }
        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
