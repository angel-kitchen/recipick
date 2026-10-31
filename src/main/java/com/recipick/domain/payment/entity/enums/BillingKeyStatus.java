package com.recipick.domain.payment.entity.enums;

public enum BillingKeyStatus {
    ACTIVE,                 // 회원의 현재 자동결제 수단. 청구 가능
    REPLACED,               // 새 카드로 교체된 이전 키. 이미 접수된 시도만 사용
    REVOKED,                // PG가 사용 불가로 답한 키. 청구 불가
    DELETE_REQUESTED,       // PortOne 삭제 요청을 기록한 상태. 청구 불가
    DELETED                 // PortOne 삭제 확인. 청구 불가
}
