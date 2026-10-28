package com.recipick.domain.payment.service;

import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.global.exception.CommonErrorCode;
import com.recipick.global.exception.ErrorCode;
import java.util.Optional;

// 마트 거절 환불의 결과. Processor가 트랜잭션 안에서 예외를 던지는 대신 이 값을 돌려주고 커밋한다.
// HTTP 응답(200·202 또는 409·500)은 Facade·Controller가 커밋 뒤에 정한다 — 회원 취소의 PaymentCancelOutcome과 같은 방식.
// 결제 전 거절이 없어서 PaymentCancelOutcome을 그대로 쓰지 않는다
public enum MartRejectionOutcome {

    // PortOne이 취소 완료를 확인했다 (같은 키 재요청으로 COMPLETED 환불을 다시 본 경우 포함)
    COMPLETED(null),

    // PortOne 결과를 아직 확정할 수 없다. 거절 접수 상태를 두고 재실행·웹훅이 마무리한다
    PENDING(null),

    // PortOne이 cancellation.status = FAILED로 명시했다. 결제만 PAID로 되돌리고 주문은 거절 상태로 둔다
    PG_FAILED(PaymentErrorCode.PAYMENT_CANCEL_REJECTED),

    // PortOne이 요청을 처리하기 전에 거절했다(400·401·403). 결제만 PAID로 되돌리고 주문은 거절 상태로 둔다
    REQUEST_REJECTED(CommonErrorCode.EXTERNAL_SERVICE_ERROR);

    private final ErrorCode errorCode;

    MartRejectionOutcome(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public Optional<ErrorCode> errorCode() {
        return Optional.ofNullable(errorCode);
    }
}
