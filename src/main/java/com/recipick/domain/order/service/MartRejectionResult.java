package com.recipick.domain.order.service;

// 마트 거절 접수의 상태 판정. 회원 취소와 같이 응답 코드는 받은 결제 Facade가 정한다
public enum MartRejectionResult {
    REJECTED,
    ALREADY_REJECTED,
    NOT_REJECTABLE
}
