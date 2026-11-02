package com.recipick.domain.payment.service;

// 같은 결제 ID 재조회가 왜 일어났는지. 원인별로 GET을 허용하는 시도 상태가 다르다 —
// SCHEDULED(복구 Sweep·실행기 자기 POST 뒤)는 READY·신선한 DISPATCHING·MANUAL_REVIEW·확정 시도를 조회하지 않고,
// VERIFIED_WEBHOOK(서명 검증된 PortOne 웹훅)·ADMIN_RECHECK(운영자 재조회)는 차단된 회차 외에는 모두 조회한다.
// 어떤 원인이든 POST는 없다
public enum MembershipReconciliationTrigger { SCHEDULED, VERIFIED_WEBHOOK, ADMIN_RECHECK }
