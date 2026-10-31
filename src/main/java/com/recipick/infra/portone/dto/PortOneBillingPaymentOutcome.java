package com.recipick.infra.portone.dto;

// PortOne이 빌링키 청구 요청에 무엇이라 답했는지. FAILED가 없다 — HTTP 오류 응답은 "승인되지 않았다"의
// 증명이 아니라서, 실제 결제 결과는 호출자가 같은 결제 ID로 GET /payments/{id}를 다시 조회해 정한다
public enum PortOneBillingPaymentOutcome {

    // 200 + payment.pgTxId·paidAt. 성공 후보일 뿐이고 확정은 재조회가 한다
    PAID,

    // 409 ALREADY_PAID. 같은 결제 ID로 이미 결제됐다(재시도·중복 호출)
    ALREADY_PAID,

    // 404 BILLING_KEY_NOT_FOUND·409 BILLING_KEY_ALREADY_DELETED. 이 키로는 청구할 수 없다(키 REVOKED 판정은 4단계)
    BILLING_KEY_UNUSABLE,

    // 400·401·403·404 CHANNEL_NOT_FOUND·그 밖의 409. PortOne이 요청을 처리하기 전에 거절했다(우리 요청·설정 문제)
    REJECTED,

    // 200인데 필수 필드 누락·5xx(502 PG_PROVIDER 포함)·timeout·연결 실패·해석 불가. 결제됐는지 알 수 없다
    UNKNOWN
}
