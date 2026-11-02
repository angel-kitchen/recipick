package com.recipick.domain.member.service;

import com.recipick.global.exception.ErrorCode;
import java.util.Objects;

public record WithdrawalPaymentMethodPreparation(Status status, ErrorCode errorCode) {
    public enum Status {
        READY, PENDING, REVIEW_REQUIRED
    }

    public WithdrawalPaymentMethodPreparation {
        Objects.requireNonNull(status, "status");
        if ((status == Status.READY) != (errorCode == null)) {
            throw new IllegalArgumentException("탈퇴 준비 결과 조합이 올바르지 않습니다");
        }
    }

    public static WithdrawalPaymentMethodPreparation ready() {
        return new WithdrawalPaymentMethodPreparation(Status.READY, null);
    }

    public boolean isReady() {
        return status == Status.READY;
    }
}
