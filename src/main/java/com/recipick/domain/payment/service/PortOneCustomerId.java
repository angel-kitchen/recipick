package com.recipick.domain.payment.service;

// PortOne에 보내는 회원의 고객 ID(SDK customer.customerId, API customer.id). 카드 등록 발급 요청·빌링키 소유 검증·빌링키 청구가
// 모두 이 값을 써야 PortOne 쪽 고객이 하나로 묶이고 소유 확인이 맞는다.
// 회원 ID 숫자만 보내면("6") 토스페이먼츠 customerKey 규칙(영문 대소문자·숫자·-*=.@, 2~255자)에 걸려 INVALID_CUSTOMER_KEY로
// 발급이 거절된다(2026-11-08 토스 테스트 채널 검증). 접두어를 붙여 PG와 무관하게 규칙을 만족시킨다
public final class PortOneCustomerId {

    static final String PREFIX = "member-";

    private PortOneCustomerId() {
    }

    public static String of(Long memberId) {
        return PREFIX + memberId;
    }
}
