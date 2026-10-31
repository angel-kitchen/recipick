package com.recipick.infra.portone.dto;

import com.recipick.global.common.TimeConstants;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

// 도메인이 쓰는 PortOne 빌링키 조회 결과. 시각은 한국 시간 LocalDateTime으로 바꾼다.
// 채널은 key와 type을 한 객체로 들고 다닌다 — 검증기가 "우리 채널 키인 그 객체"의 type을 봐야 하기 때문
public record PortOneBillingKeyInfo(
    PortOneBillingKeyStatus status,
    String billingKey,
    String storeId,
    List<Channel> channels,
    String customerId,
    String issueId,
    boolean cardMethod,
    LocalDateTime issuedAt,
    LocalDateTime deletedAt
) {

    public static final String CARD_METHOD_TYPE = "BillingKeyPaymentMethodCard";

    public record Channel(
        String key,
        String type
    ) {
    }

    public PortOneBillingKeyInfo {
        channels = channels == null ? List.of() : List.copyOf(channels);
    }

    public static PortOneBillingKeyInfo from(PortOneBillingKeyInfoResponse response) {
        return new PortOneBillingKeyInfo(
            response.status(),
            response.billingKey(),
            response.storeId(),
            response.channels() == null
                ? List.of()
                : response.channels().stream().map(channel -> new Channel(channel.key(), channel.type())).toList(),
            response.customer() == null ? null : response.customer().id(),
            response.issueId(),
            response.methods() != null
                && response.methods().stream().anyMatch(method -> CARD_METHOD_TYPE.equals(method.type())),
            toKst(response.issuedAt()),
            toKst(response.deletedAt())
        );
    }

    // 키가 같은 채널 객체. 검증기는 이 객체의 type만 본다
    public Optional<Channel> channelOf(String key) {
        return channels.stream().filter(channel -> key.equals(channel.key())).findFirst();
    }

    private static LocalDateTime toKst(OffsetDateTime time) {
        return time == null ? null : time.atZoneSameInstant(TimeConstants.ZONE_ID).toLocalDateTime();
    }
}
