package com.recipick.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recipick.domain.payment.entity.enums.BillingKeyIssueRequestStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BillingKeyIssueRequestTest {

    private static final Long MEMBER_ID = 7L;
    private static final String ISSUE_ID = "rcp-bk-0f8a1c2e-6d4b-4a1e-9c3f-1234567890ab";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 14, 0);
    private static final String KEY = "billing-key-test-0001";

    @Test
    @DisplayName("발급 요청은 REQUESTED로 시작하고 30분 뒤에 만료된다")
    void requested() {
        // when
        BillingKeyIssueRequest request = BillingKeyIssueRequest.requested(MEMBER_ID, ISSUE_ID, NOW);

        // then
        assertThat(request.getStatus()).isEqualTo(BillingKeyIssueRequestStatus.REQUESTED);
        assertThat(request.getIssueId()).isEqualTo(ISSUE_ID);
        assertThat(request.getExpiresAt()).isEqualTo(NOW.plusMinutes(30));
        assertThat(request.isOwnedBy(MEMBER_ID)).isTrue();
        assertThat(request.isOwnedBy(MEMBER_ID + 1)).isFalse();
        assertThat(request.isUsed()).isFalse();
        assertThat(request.getUsedAt()).isNull();
        assertThat(request.getBillingKey()).isNull();
    }

    @Test
    @DisplayName("만료 1초 전까지는 쓸 수 있고 만료 시각 정각부터는 쓸 수 없다")
    void validityBoundary() {
        // given
        BillingKeyIssueRequest request = BillingKeyIssueRequest.requested(MEMBER_ID, ISSUE_ID, NOW);
        LocalDateTime expiresAt = NOW.plusMinutes(30);

        // then
        assertThat(request.isWithinValidity(expiresAt.minusSeconds(1))).isTrue();
        assertThat(request.isUsableAt(expiresAt.minusSeconds(1))).isTrue();
        assertThat(request.isWithinValidity(expiresAt)).isFalse();
        assertThat(request.isUsableAt(expiresAt)).isFalse();
    }

    @Test
    @DisplayName("사용 처리하면 USED가 되고 그 키에만 묶인다")
    void markUsed() {
        // given
        BillingKeyIssueRequest request = BillingKeyIssueRequest.requested(MEMBER_ID, ISSUE_ID, NOW);
        BillingKey key = BillingKey.issued(MEMBER_ID, KEY, NOW);

        // when
        request.markUsed(key, NOW.plusMinutes(1));

        // then
        assertThat(request.getStatus()).isEqualTo(BillingKeyIssueRequestStatus.USED);
        assertThat(request.isUsed()).isTrue();
        assertThat(request.getUsedAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(request.getBillingKey()).isSameAs(key);
        assertThat(request.isBoundTo(KEY)).isTrue();
        assertThat(request.isBoundTo("another-key")).isFalse();
        assertThat(request.isUsableAt(NOW.plusMinutes(2))).isFalse();
        // 유효기간 자체는 USED 뒤에도 같은 기준이다 — 재활성화 허용 여부를 가른다
        assertThat(request.isWithinValidity(NOW.plusMinutes(29))).isTrue();
        assertThat(request.isWithinValidity(NOW.plusMinutes(31))).isFalse();
    }

    @Test
    @DisplayName("REQUESTED가 아닌 요청은 묶인 키가 없으므로 어떤 키에도 묶이지 않는다")
    void requestedIsBoundToNothing() {
        // given
        BillingKeyIssueRequest request = BillingKeyIssueRequest.requested(MEMBER_ID, ISSUE_ID, NOW);

        // then
        assertThat(request.isBoundTo(KEY)).isFalse();
    }

    @Test
    @DisplayName("이미 사용된 요청을 다시 사용 처리하면 예외다 — 같은 키 재제출은 이 메서드를 부르지 않는다")
    void markUsedTwice() {
        // given
        BillingKeyIssueRequest request = BillingKeyIssueRequest.requested(MEMBER_ID, ISSUE_ID, NOW);
        BillingKey key = BillingKey.issued(MEMBER_ID, KEY, NOW);
        request.markUsed(key, NOW);

        // when
        Throwable thrown = catchThrowable(() -> request.markUsed(key, NOW.plusMinutes(1)));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(request.getUsedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("REQUESTED 요청은 유효기간 안에서만 어떤 키든 첫 제출을 허용한다")
    void requestedAllowsFirstSubmissionWithinValidity() {
        // given
        BillingKeyIssueRequest request = BillingKeyIssueRequest.requested(MEMBER_ID, ISSUE_ID, NOW);

        // then
        assertThat(request.allowsSubmissionOf(KEY, NOW.plusMinutes(29))).isTrue();
        assertThat(request.allowsSubmissionOf(KEY, NOW.plusMinutes(30))).isFalse();
    }

    @Test
    @DisplayName("USED 요청은 묶인 키의 재제출만 허용하고 다른 키는 거절한다")
    void usedAllowsOnlyBoundKey() {
        // given
        BillingKeyIssueRequest request = BillingKeyIssueRequest.requested(MEMBER_ID, ISSUE_ID, NOW);
        BillingKey key = BillingKey.issued(MEMBER_ID, KEY, NOW);
        request.markUsed(key, NOW);

        // then
        assertThat(request.allowsSubmissionOf(KEY, NOW.plusMinutes(1))).isTrue();
        assertThat(request.allowsSubmissionOf("another-key", NOW.plusMinutes(1))).isFalse();
    }

    @Test
    @DisplayName("묶인 키가 ACTIVE인 멱등 재제출은 유효기간이 지나도 허용한다 — 상태를 바꾸지 않는 요청이다")
    void usedActiveKeyIgnoresValidity() {
        // given
        BillingKeyIssueRequest request = BillingKeyIssueRequest.requested(MEMBER_ID, ISSUE_ID, NOW);
        BillingKey key = BillingKey.issued(MEMBER_ID, KEY, NOW);
        request.markUsed(key, NOW);

        // then
        assertThat(request.allowsSubmissionOf(KEY, NOW.plusDays(3))).isTrue();
    }

    @Test
    @DisplayName("묶인 키가 REPLACED인 재활성화는 유효기간 안에서만 허용한다 — 결제수단을 바꾸는 새 작업이다")
    void usedReplacedKeyRequiresValidity() {
        // given
        BillingKeyIssueRequest request = BillingKeyIssueRequest.requested(MEMBER_ID, ISSUE_ID, NOW);
        BillingKey key = BillingKey.issued(MEMBER_ID, KEY, NOW);
        request.markUsed(key, NOW);
        key.replace();

        // then
        assertThat(request.allowsSubmissionOf(KEY, NOW.plusMinutes(29))).isTrue();
        assertThat(request.allowsSubmissionOf(KEY, NOW.plusMinutes(30))).isFalse();
    }

    @Test
    @DisplayName("toString에 토큰이 없다")
    void toStringHidesToken() {
        // given
        BillingKeyIssueRequest request = BillingKeyIssueRequest.requested(MEMBER_ID, ISSUE_ID, NOW);

        // then
        assertThat(request.toString()).doesNotContain(ISSUE_ID);
        assertThat(request.toString()).contains("REQUESTED");
    }
}
