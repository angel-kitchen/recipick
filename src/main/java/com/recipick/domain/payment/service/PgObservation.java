package com.recipick.domain.payment.service;

import com.recipick.infra.portone.dto.PortOnePayment;

// 같은 결제 ID GET 한 번의 관측 — 회차 금액과 대조한 판정(verdict)과 PG 응답 자체(payment). 판정만으로는 취소 계열(CANCELED·PARTIAL_CANCELED)이
// 신뢰 불가로 묶이므로, 결제 결과 반영이 수납 PAID 시도의 취소 관측을 환불 정합화로 넘기려면 응답이 함께 필요하다.
// 조회가 실패했으면 payment는 null이고 verdict가 그 사실을 담는다. 실행기의 POST 뒤 GET과 재조회(웹훅·복구·관리자)가 같은 타입을 쓴다
public record PgObservation(MembershipPgVerdict verdict, PortOnePayment payment) {

    public static PgObservation withoutPayment(MembershipPgVerdict verdict) {
        return new PgObservation(verdict, null);
    }

    // PG가 이 결제를 취소(전체·부분) 상태로 돌려줬다
    public boolean cancellationObserved() {
        return payment != null && payment.status() != null && switch (payment.status()) {
            case CANCELED, PARTIAL_CANCELED -> true;
            default -> false;
        };
    }
}
