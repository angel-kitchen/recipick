package com.recipick.domain.payment.dto;

import com.recipick.domain.payment.entity.Payment;

// 결제 취소 TX1의 결과. 트랜잭션 안에서 이미 끝났으면 finishedResult가 있고,
// PortOne을 불러야 하면 finishedResult가 비고 호출 인자가 찬다. Facade가 이걸 보고 PortOne 호출 → TX2로 이어 간다
public record PaymentCancelPreparation(
    PaymentCancelResult finishedResult,
    Long refundId,
    String portonePaymentId,
    Long amount
) {
    // 허용되는 모양은 정확히 둘: 끝난 결과만 있음 / 호출 정보 셋이 전부 있고 결과는 없음.
    // record 생성자는  public이라 팩토리만으론 못 막음
    public PaymentCancelPreparation {
        boolean hasFinishedResult = finishedResult != null;
        boolean hasAnyCallInfo = refundId != null || portonePaymentId != null || amount != null;
        boolean hasAllCallInfo = refundId != null && portonePaymentId != null && amount != null;
        boolean validFinished = hasFinishedResult && !hasAnyCallInfo;
        boolean validCall = !hasFinishedResult && hasAllCallInfo;
        if (!validFinished && !validCall) {
            throw new IllegalArgumentException("끝난 결과 또는 PortOne 호출 정보 중 하나만 완전하게 있어야 합니다.");
    }
}

    public static PaymentCancelPreparation finished(PaymentCancelResult result) {
        return new PaymentCancelPreparation(result, null, null, null);
    }

    public static PaymentCancelPreparation callPortOne(Long refundId, String portonePaymentId, Long amount) {
        return new PaymentCancelPreparation(null, refundId, portonePaymentId, amount);
    }

    public boolean requiresPortOneCall() {
        return finishedResult == null;
    }
}
