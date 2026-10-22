package com.recipick.domain.payment.dto;


import com.recipick.domain.payment.entity.enums.RefundStatus;

public record RefundSummary(long completedAmount, RefundStatus latestStatus) {

    public static final RefundSummary NONE = new RefundSummary(0L, null);
}
