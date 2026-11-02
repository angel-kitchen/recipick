package com.recipick.domain.payment.service;

// 멤버십 결제 웹훅 한 건의 판정. 웹훅 Inbox가 이 결과로 처리 상태(처리 완료·재시도·운영 확인)를 기록한다
public record MembershipWebhookResult(WebhookProcessOutcome outcome, String reason) {

    public static MembershipWebhookResult processed() {
        return new MembershipWebhookResult(WebhookProcessOutcome.PROCESSED, null);
    }

    public static MembershipWebhookResult retry(String reason) {
        return new MembershipWebhookResult(WebhookProcessOutcome.RETRY_PENDING, reason);
    }

    public static MembershipWebhookResult manualReview(String reason) {
        return new MembershipWebhookResult(WebhookProcessOutcome.MANUAL_REVIEW, reason);
    }
}
