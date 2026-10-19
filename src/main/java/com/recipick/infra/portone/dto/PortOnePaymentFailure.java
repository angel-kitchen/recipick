package com.recipick.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.stream.Stream;

/**
 * PortOne 결제 실패 정보. 하위 필드명(reason, pgCode, pgMessage)은 테스트 채널 응답으로 최종 확인한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOnePaymentFailure(
        String reason,
        String pgCode,
        String pgMessage
) {

    private static final int SUMMARY_MAX_LENGTH = 500;
    private static final String SEPARATOR = " | ";

    /**
     * 주문 종료 사유(reasonDetail)로 넘길 한 줄 요약. 값이 하나도 없으면 null.
     */
    public String summary() {
        String joined = String.join(SEPARATOR, Stream.of(reason, pgCode, pgMessage)
                .filter(value -> value != null && !value.isBlank())
                .toList());
        if (joined.isEmpty()) {
            return null;
        }
        return joined.length() > SUMMARY_MAX_LENGTH ? joined.substring(0, SUMMARY_MAX_LENGTH) : joined;
    }
}
