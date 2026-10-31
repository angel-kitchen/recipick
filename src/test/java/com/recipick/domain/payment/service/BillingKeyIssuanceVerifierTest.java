package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.domain.payment.service.BillingKeyIssuanceVerifier.RejectReason;
import com.recipick.infra.portone.dto.PortOneBillingKeyInfo;
import com.recipick.infra.portone.dto.PortOneBillingKeyInfo.Channel;
import com.recipick.infra.portone.dto.PortOneBillingKeyStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 설계 5-5 ④표. 입력 record를 직접 만들어 검사 순서와 사유를 본다 — Spring·외부 호출 없음
class BillingKeyIssuanceVerifierTest {

    private static final Long MEMBER_ID = 7L;
    private static final String STORE_ID = "test-store-id";
    private static final String CHANNEL_KEY = "test-channel-key";
    private static final String KEY = "billing-key-test-0001";
    private static final String ISSUE_ID = "rcp-bk-0f8a1c2e-6d4b-4a1e-9c3f-1234567890ab";
    private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 10, 31, 14, 0);
    private static final Channel OUR_TEST_CHANNEL = new Channel(CHANNEL_KEY, "TEST");
    private static final Channel OUR_LIVE_CHANNEL = new Channel(CHANNEL_KEY, "LIVE");
    private static final Channel OTHER_TEST_CHANNEL = new Channel("other-channel-key", "TEST");
    private static final Channel OTHER_LIVE_CHANNEL = new Channel("other-channel-key", "LIVE");

    private final BillingKeyIssuanceVerifier verifier = new BillingKeyIssuanceVerifier(STORE_ID, CHANNEL_KEY);

    @Test
    @DisplayName("ISSUED·키·상점·테스트 채널·issueId·customer.id·카드 수단이 모두 맞으면 통과한다")
    void passes() {
        // when
        Optional<RejectReason> reason = verify(info(List.of(OTHER_LIVE_CHANNEL, OUR_TEST_CHANNEL), "7", ISSUE_ID));

        // then
        assertThat(reason).isEmpty();
    }

    @Test
    @DisplayName("customer.id가 없어도 issueId가 맞으면 통과한다 — 소유의 1차 증거는 issueId다")
    void passesWithoutCustomerId() {
        // when
        Optional<RejectReason> reason = verify(info(List.of(OUR_TEST_CHANNEL), null, ISSUE_ID));

        // then
        assertThat(reason).isEmpty();
    }

    @Test
    @DisplayName("DELETED 키는 NOT_ISSUED다")
    void notIssued() {
        // given
        PortOneBillingKeyInfo info = new PortOneBillingKeyInfo(PortOneBillingKeyStatus.DELETED, KEY, STORE_ID,
            List.of(OUR_TEST_CHANNEL), "7", ISSUE_ID, true, ISSUED_AT, ISSUED_AT.plusHours(1));

        // then
        assertThat(verify(info)).contains(RejectReason.NOT_ISSUED);
    }

    @Test
    @DisplayName("응답의 billingKey가 요청 키와 다르면 KEY_MISMATCH다")
    void keyMismatch() {
        // given
        PortOneBillingKeyInfo info = new PortOneBillingKeyInfo(PortOneBillingKeyStatus.ISSUED, "another-key",
            STORE_ID, List.of(OUR_TEST_CHANNEL), "7", ISSUE_ID, true, ISSUED_AT, null);

        // then
        assertThat(verify(info)).contains(RejectReason.KEY_MISMATCH);
    }

    @Test
    @DisplayName("다른 상점의 키는 STORE_MISMATCH다")
    void storeMismatch() {
        // given
        PortOneBillingKeyInfo info = new PortOneBillingKeyInfo(PortOneBillingKeyStatus.ISSUED, KEY,
            "other-store", List.of(OUR_TEST_CHANNEL), "7", ISSUE_ID, true, ISSUED_AT, null);

        // then
        assertThat(verify(info)).contains(RejectReason.STORE_MISMATCH);
    }

    @Test
    @DisplayName("우리 채널 키가 없으면 CHANNEL_MISMATCH다 — 채널 배열이 비어 있어도 같다")
    void channelMismatch() {
        // then
        assertThat(verify(info(List.of(OTHER_TEST_CHANNEL), "7", ISSUE_ID))).contains(RejectReason.CHANNEL_MISMATCH);
        assertThat(verify(info(List.of(), "7", ISSUE_ID))).contains(RejectReason.CHANNEL_MISMATCH);
    }

    @Test
    @DisplayName("우리 채널 키의 채널이 LIVE면 거절한다 — 다른 채널이 TEST여도 증거가 아니다")
    void liveChannelBlocked() {
        // then
        assertThat(verify(info(List.of(OUR_LIVE_CHANNEL, OTHER_TEST_CHANNEL), "7", ISSUE_ID)))
            .contains(RejectReason.LIVE_CHANNEL_BLOCKED);
    }

    @Test
    @DisplayName("우리 채널의 type이 없거나 모르는 값이면 TEST가 아니므로 거절한다")
    void unknownChannelTypeBlocked() {
        // then
        assertThat(verify(info(List.of(new Channel(CHANNEL_KEY, null)), "7", ISSUE_ID)))
            .contains(RejectReason.LIVE_CHANNEL_BLOCKED);
        assertThat(verify(info(List.of(new Channel(CHANNEL_KEY, "SANDBOX")), "7", ISSUE_ID)))
            .contains(RejectReason.LIVE_CHANNEL_BLOCKED);
    }

    @Test
    @DisplayName("PortOne이 issueId를 돌려주지 않으면 customer.id가 맞아도 소유를 증명할 수 없어 거절한다")
    void ownershipUnverifiable() {
        // then
        assertThat(verify(info(List.of(OUR_TEST_CHANNEL), "7", null)))
            .contains(RejectReason.OWNERSHIP_UNVERIFIABLE);
        assertThat(verify(info(List.of(OUR_TEST_CHANNEL), "7", " ")))
            .contains(RejectReason.OWNERSHIP_UNVERIFIABLE);
    }

    @Test
    @DisplayName("issueId가 서버가 발급한 토큰과 다르면 ISSUE_ID_MISMATCH다")
    void issueIdMismatch() {
        // then
        assertThat(verify(info(List.of(OUR_TEST_CHANNEL), "7", "rcp-bk-someone-else")))
            .contains(RejectReason.ISSUE_ID_MISMATCH);
    }

    @Test
    @DisplayName("customer.id가 있는데 로그인 회원과 다르면 CUSTOMER_MISMATCH다")
    void customerMismatch() {
        // then
        assertThat(verify(info(List.of(OUR_TEST_CHANNEL), "8", ISSUE_ID))).contains(RejectReason.CUSTOMER_MISMATCH);
    }

    @Test
    @DisplayName("카드 수단이 아니면 NOT_CARD다")
    void notCard() {
        // given
        PortOneBillingKeyInfo info = new PortOneBillingKeyInfo(PortOneBillingKeyStatus.ISSUED, KEY, STORE_ID,
            List.of(OUR_TEST_CHANNEL), "7", ISSUE_ID, false, ISSUED_AT, null);

        // then
        assertThat(verify(info)).contains(RejectReason.NOT_CARD);
    }

    @Test
    @DisplayName("설정에 상점·채널이 비어 있으면 어떤 응답도 통과하지 못한다")
    void emptyConfigNeverPasses() {
        // given
        BillingKeyIssuanceVerifier unconfigured = new BillingKeyIssuanceVerifier(null, null);

        // when
        Optional<RejectReason> reason = unconfigured.verify(MEMBER_ID, KEY, ISSUE_ID,
            info(List.of(OUR_TEST_CHANNEL), "7", ISSUE_ID));

        // then
        assertThat(reason).contains(RejectReason.STORE_MISMATCH);
    }

    private Optional<RejectReason> verify(PortOneBillingKeyInfo info) {
        return verifier.verify(MEMBER_ID, KEY, ISSUE_ID, info);
    }

    private static PortOneBillingKeyInfo info(List<Channel> channels, String customerId, String issueId) {
        return new PortOneBillingKeyInfo(PortOneBillingKeyStatus.ISSUED, KEY, STORE_ID, channels, customerId,
            issueId, true, ISSUED_AT, null);
    }
}
