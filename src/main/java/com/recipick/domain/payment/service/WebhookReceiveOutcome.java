package com.recipick.domain.payment.service;

// 웹훅 수신 결과. 셋 다 200.
public enum WebhookReceiveOutcome {
    RECEIVED,             // 저장했고 Worker가 처리할 차례
    IGNORED,              // 저장했지만 처리 대상이 아님
    DUPLICATE             // 같은 webhook_id를 이미 받았다. 저장하지 않는다
}
