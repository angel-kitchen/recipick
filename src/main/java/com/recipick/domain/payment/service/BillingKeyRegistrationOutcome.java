package com.recipick.domain.payment.service;

// 등록 요청의 결과. 모두 200이고 응답 data.outcome으로 내려간다
public enum BillingKeyRegistrationOutcome {
    REGISTERED,      // 새 키를 ACTIVE로 저장
    REPLACED,        // 이전 ACTIVE 키를 REPLACED로 바꾸고 새 키를 ACTIVE로 저장
    ALREADY_ACTIVE,  // 같은 키가 이미 ACTIVE. 변경 없음
    REACTIVATED      // 내 REPLACED 키를 다시 ACTIVE로
}
