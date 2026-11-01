package com.recipick.domain.payment.config;

// 신규 청구 플래그를 읽는 한 곳. 실행기는 선점 전과 PG POST 직전에 두 번 묻는다
// 운영 구현은 PropertiesMembershipBillingGate(설정값 고정). 테스트는 토글 가능한 구현을 @Primary로 끼워
// "실행 도중 플래그가 바뀌는" 상황을 재현한다 — Mockito 없이
public interface MembershipBillingGate {

    boolean newChargeEnabled();
}
