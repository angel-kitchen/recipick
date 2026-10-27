package com.recipick.domain.payment.service;

// 외부 전액 취소 반영 결과와 사유. RETRY·NOT_APPLICABLE의 사유는 웹훅 기록에 그대로 남아 운영자가 원인을 바로 읽는다
public record ExternalCancellationResult(ExternalCancellationOutcome outcome, String reason) {

    public static ExternalCancellationResult reconciled() {
        return new ExternalCancellationResult(ExternalCancellationOutcome.RECONCILED, null);
    }

    public static ExternalCancellationResult alreadyReconciled() {
        return new ExternalCancellationResult(ExternalCancellationOutcome.ALREADY_RECONCILED, null);
    }

    public static ExternalCancellationResult retry(String reason) {
        return new ExternalCancellationResult(ExternalCancellationOutcome.RETRY, reason);
    }

    public static ExternalCancellationResult notApplicable(String reason) {
        return new ExternalCancellationResult(ExternalCancellationOutcome.NOT_APPLICABLE, reason);
    }
}
