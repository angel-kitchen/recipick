package com.recipick.domain.payment.service;

import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.global.exception.ErrorCode;
import java.util.Optional;

// 결제 취소의 최종 결과. Processor가 트랜잭션 안에서 예외를 던지는 대신 이 값을 돌려주고 커밋한다.
// HTTP 응답(200·202 또는 409·500)은 Facade·Controller가 커밋 뒤에 정한다 — PaymentApprovalOutcome과 같은 방식
public enum PaymentCancelOutcome {

    // 결제 전 취소. PG를 거치지 않았다
    CANCELED_BEFORE_PAY(null),

    // PortOne이 취소 완료를 확인했다 (같은 키 재요청으로 COMPLETED 환불을 다시 본 경우 포함)
    CANCELED(null),

    // PortOne 결과를 아직 확정할 수 없다. REQUESTED 유지, 웹훅·재조회가 마무리한다
    PENDING(null),

    // PortOne이 cancellation.status = FAILED로 명시했다. PAID로 되돌렸다
    PG_FAILED(PaymentErrorCode.PAYMENT_CANCEL_REJECTED),

    // PortOne이 요청을 처리하기 전에 거절했다(400·401·403). PAID로 되돌렸다
    REQUEST_REJECTED(CommonErrorCode.EXTERNAL_SERVICE_ERROR);

    private final ErrorCode errorCode;

    PaymentCancelOutcome(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public Optional<ErrorCode> errorCode() {
        return Optional.ofNullable(errorCode);
    }
}
