package com.recipick.domain.payment.service;

// 재조회 한 번의 결과. outcome은 반영 결과, verdict는 그 재조회의 PG GET 판정 — GET을 하지 않았으면 null.
// 운영자 재조회 응답이 PG 관측값(판정 종류·결제 시각·수납액)을 보여 주기 위해 outcome과 함께 돌려준다.
// 같은 요청에서 GET을 두 번 하지 않는다 — TX2에 넘긴 verdict가 그대로 여기에 담긴다
public record MembershipReconciliationResult(MembershipChargeOutcome outcome, MembershipPgVerdict verdict) {

    public static MembershipReconciliationResult withoutLookup(MembershipChargeOutcome outcome) {
        return new MembershipReconciliationResult(outcome, null);
    }

    public boolean lookedUp() {
        return verdict != null;
    }
}
