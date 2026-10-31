package com.recipick.domain.payment.service;

import com.recipick.infra.portone.dto.PortOneBillingKeyInfo;
import com.recipick.infra.portone.dto.PortOneBillingKeyStatus;
import java.util.Optional;

// PortOne 빌링키 조회 결과가 "이 회원이 우리 상점·테스트 채널에서 방금 발급받은 카드 빌링키"인지 판정한다.
// Spring을 모르는 순수 클래스다(BillingKeyConfig가 PortOne 설정으로 빈을 만든다). 외부 호출·DB 없이 입력만 본다.
// 소유의 증거는 PortOne이 돌려준 issueId가 서버가 발급·저장한 토큰과 같다는 사실이다. customer.id는 보조 확인이다.
// 2a는 테스트 채널 빌링키만 등록한다. 실채널 허용은 설정이 아니라 코드 변경(암호화 적용 PR)으로만 연다 — 운영자가
// 플래그를 켜도 실채널 설정이면 여기서 거절된다
public final class BillingKeyIssuanceVerifier {

    static final String ALLOWED_CHANNEL_TYPE = "TEST";

    private final String storeId;
    private final String channelKey;

    public BillingKeyIssuanceVerifier(String storeId, String channelKey) {
        this.storeId = storeId;
        this.channelKey = channelKey;
    }

    // 거절 사유가 있으면 그 사유, 모두 통과하면 empty. 검사 순서는 설계 5-5 ④표와 같다
    public Optional<RejectReason> verify(Long memberId, String requestedKey, String expectedIssueId,
                                         PortOneBillingKeyInfo info) {
        if (info.status() != PortOneBillingKeyStatus.ISSUED) {
            return Optional.of(RejectReason.NOT_ISSUED);
        }
        if (!requestedKey.equals(info.billingKey())) {
            return Optional.of(RejectReason.KEY_MISMATCH);
        }
        if (storeId == null || !storeId.equals(info.storeId())) {
            return Optional.of(RejectReason.STORE_MISMATCH);
        }
        Optional<PortOneBillingKeyInfo.Channel> channel =
            channelKey == null ? Optional.empty() : info.channelOf(channelKey);
        if (channel.isEmpty()) {
            return Optional.of(RejectReason.CHANNEL_MISMATCH);
        }
        // 키가 일치한 그 채널 객체의 type만 본다. 다른 채널의 TEST 표시는 증거가 아니다
        if (!ALLOWED_CHANNEL_TYPE.equals(channel.get().type())) {
            return Optional.of(RejectReason.LIVE_CHANNEL_BLOCKED);
        }
        if (info.issueId() == null || info.issueId().isBlank()) {
            return Optional.of(RejectReason.OWNERSHIP_UNVERIFIABLE);
        }
        if (!info.issueId().equals(expectedIssueId)) {
            return Optional.of(RejectReason.ISSUE_ID_MISMATCH);
        }
        if (info.customerId() != null && !info.customerId().isBlank()
            && !String.valueOf(memberId).equals(info.customerId())) {
            return Optional.of(RejectReason.CUSTOMER_MISMATCH);
        }
        if (!info.cardMethod()) {
            return Optional.of(RejectReason.NOT_CARD);
        }
        return Optional.empty();
    }

    public enum RejectReason {
        NOT_ISSUED,
        KEY_MISMATCH,
        STORE_MISMATCH,
        CHANNEL_MISMATCH,
        LIVE_CHANNEL_BLOCKED,      // 채널이 TEST가 아니다. 운영에서 나면 설정이 실채널이라는 신호 — ERROR
        OWNERSHIP_UNVERIFIABLE,    // PortOne이 issueId를 돌려주지 않았다. E-2 결과와 다른 동작 — ERROR
        ISSUE_ID_MISMATCH,
        CUSTOMER_MISMATCH,
        NOT_CARD
    }
}
