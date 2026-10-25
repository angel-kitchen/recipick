package com.recipick.domain.payment.dto;

import com.recipick.domain.payment.dto.response.PaymentCancelResponse;
import com.recipick.domain.payment.service.PaymentCancelOutcome;

import java.util.Objects;

// Processor가 트랜잭션 안에서 만드는 취소 결과. outcome은 Facade가 예외를 던질지 정하는데, reponse는 Controller응답에 쓴다
// 실패 outcome(PG_FAILED·REQUEST_REJECTED)은 Facade가 예외로 바꾸므로 Controller까지 오지 않는다
public record PaymentCancelResult(
    PaymentCancelOutcome outcome,
    PaymentCancelResponse response
) {
    public PaymentCancelResult {
        Objects.requireNonNull(outcome, "outcome은 필수입니다.");
        Objects.requireNonNull(response, "response는 필수입니다.");
    }
}
