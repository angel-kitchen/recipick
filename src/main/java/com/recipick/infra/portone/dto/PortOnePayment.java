package com.recipick.infra.portone.dto;

import com.recipick.global.common.TimeConstants;
import com.recipick.infra.portone.PortOneCustomDataParser;
import java.time.LocalDateTime;

/**
 * 도메인이 쓰는 PortOne 결제 조회 결과. 시각은 한국 시간 LocalDateTime으로, customData는 주문 ID로
 * 바꿔 두어 domain Service가 Jackson 없이 값만 비교한다.
 *
 * @param paidAt            PortOne이 확인한 결제 완료 시각(KST). 미결제면 null
 * @param customDataOrderId customData에서 읽은 주문 ID. 없거나 해석 불가면 null (잠정 구현)
 * @param failureSummary    PG 실패 사유 한 줄 요약. 실패 정보가 없으면 null
 */
public record PortOnePayment(
        String id,
        String transactionId,
        PortOnePaymentStatus status,
        Long totalAmount,
        String currency,
        LocalDateTime paidAt,
        Long customDataOrderId,
        String failureSummary
) {

    public static PortOnePayment from(PortOnePaymentResponse response) {
        return new PortOnePayment(
                response.id(),
                response.transactionId(),
                response.status(),
                response.amount() == null ? null : response.amount().total(),
                response.currency(),
                response.paidAt() == null
                        ? null
                        : response.paidAt().atZoneSameInstant(TimeConstants.ZONE_ID).toLocalDateTime(),
                PortOneCustomDataParser.orderIdOf(response.customData()),
                response.failure() == null ? null : response.failure().summary()
        );
    }
}
