package com.recipick.infra.portone.dto;

import java.util.Arrays;
import java.util.Optional;

// 정합화가 처리하는 PortOne 웹훅 이벤트. 그 밖의 type(BillingKey.*, Transaction.Ready 등)은 받되 IGNORED로 기록한다.
// 이름은 팀 철자(CANCELED)로 두고 PortOne 철자는 wireName에만 남긴다.
public enum PortOneWebhookType {

    PAID("Transaction.Paid"),
    FAILED("Transaction.Failed"),
    CANCEL_PENDING("Transaction.CancelPending"),
    CANCELED("Transaction.Cancelled"),
    PARTIAL_CANCELED("Transaction.PartialCancelled");

    private final String wireName;

    PortOneWebhookType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static Optional<PortOneWebhookType> of(String wireName) {
        return Arrays.stream(values())
            .filter(type -> type.wireName.equals(wireName))
            .findFirst();
    }
}
