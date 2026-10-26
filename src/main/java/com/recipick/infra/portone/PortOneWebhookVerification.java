package com.recipick.infra.portone;

// 웹훅 서명 검증 결과. 예외 대신 값으로 돌려줘 호출자가 응답 코드(400·500)를 정한다
public enum PortOneWebhookVerification {

    VALID,
    NOT_CONFIGURED, // 시크릿이 설정되지 않았다. 우리 쪽 설정 문제라 PortOne이 다시 보내도록 500으로 답한다
    MISSING_HEADERS, // webhook-id·webhook-timestamp·webhook-signature 중 하나가 없거나 비었다
    INVALID_TIMESTAMP, // webhook-timestamp가 epoch 초 숫자가 아니다
    TIMESTAMP_OUT_OF_RANGE, // webhook-timestamp가 현재 시각 ±5분을 벗어났다. 오래된 웹훅을 다시 보내는 재생 공격 방지
    INVALID_SIGNATURE; // 일치하는 v1 서명이 없다

    public boolean isValid() {
        return this ==VALID;
    }
}
