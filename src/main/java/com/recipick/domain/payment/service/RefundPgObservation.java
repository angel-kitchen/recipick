package com.recipick.domain.payment.service;

import com.recipick.infra.portone.dto.PortOneCancellation;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;

// 관리자 재조회 응답에 담는 이번 GET의 관측값. 저장된 환불 상태와 분리한다 — GET이 실패했으면 lookedUp=false·code만 있고,
// 저장된 상태가 COMPLETED라고 해서 "이번 조회도 성공"으로 보이게 하지 않는다. 취소 필드는 이 환불에 매칭된 취소 내역이 있을 때만 채운다
public record RefundPgObservation(
    boolean lookedUp,
    PortOnePaymentStatus paymentStatus,
    String cancellationId,
    PortOneCancellationStatus cancellationStatus,
    Long cancellationAmount,
    String code
) {

    public static RefundPgObservation lookupFailed(String code) {
        return new RefundPgObservation(false, null, null, null, null, code);
    }

    public static RefundPgObservation of(PortOnePayment payment, PortOneCancellation matched) {
        return new RefundPgObservation(true, payment.status(),
            matched == null ? null : matched.id(),
            matched == null ? null : matched.status(),
            matched == null ? null : matched.totalAmount(),
            null);
    }
}
