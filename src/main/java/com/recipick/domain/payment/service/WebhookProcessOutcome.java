package com.recipick.domain.payment.service;

// Worker가 꺼낸 웹훅 이벤트 하나의 처리 결과. SKIPPED만 "기록하지 않음"이고 나머지는 WEBHOOK_EVENT에 그대로 기록된 상태다
public enum WebhookProcessOutcome {

    // 이미 끝났거나 다른 Worker가 처리 중이거나 없는 행이라 잡지 못했거나,
    // 처리 도중 새 Worker로 처리권이 넘어가 결과를 기록하지 않았다.

    SKIPPED,
    PROCESSED,
    IGNORED,
    RETRY_PENDING,      // backoff 뒤 다시 한다. 다음 실행 시각은 큐(ZSET score)가 갖는다
    MANUAL_REVIEW
}
