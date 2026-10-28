package com.recipick.domain.payment.dto;

import com.recipick.domain.payment.service.MartRejectionOutcome;
import java.util.Objects;

// 마트 거절 TX1의 결과. 같은 키 재요청이 이미 끝난 환불을 재현하면 finished, PortOne 실행이 필요하면 execute다.
// 실행 대상은 응답의 환불 ID 하나뿐이다 — PortOne 결제 ID·금액은 실행기가 환불 스냅샷에서 다시 읽는다
public record MartRejectionPreparation(
    MartRejectionPaymentResult result,
    boolean requiresExecution
) {
    // 아직 결과를 모르는(PENDING) 거절만 실행이 필요하고, 실행이 필요한 거절은 반드시 PENDING이다
    public MartRejectionPreparation {
        Objects.requireNonNull(result, "result는 필수입니다.");
        if (requiresExecution != (result.outcome() == MartRejectionOutcome.PENDING)) {
            throw new IllegalArgumentException("실행이 필요한 거절은 PENDING이어야 하고, 끝난 거절은 PENDING일 수 없습니다.");
        }
    }

    public static MartRejectionPreparation finished(MartRejectionPaymentResult result) {
        return new MartRejectionPreparation(result, false);
    }

    public static MartRejectionPreparation execute(MartRejectionPaymentResult result) {
        return new MartRejectionPreparation(result, true);
    }

    public Long refundId() {
        return result.response().refundId();
    }
}
