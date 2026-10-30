package com.recipick.domain.payment.dto.response;

import com.recipick.domain.payment.entity.PointEarning;
import com.recipick.domain.payment.entity.enums.PointEarningStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 적립건 하나. DB의 남은 양을 그대로 보여주면 만료 배치 전의 만료 잔량이 쓸 수 있는 것처럼 보이므로, 오늘 날짜로 쓸 수 있는 잔량만 availableRemainingAmount로 내보낸다. DB 값은 조회 때문에 고치지 않는다
public record PointEarningResponse(
    Long earningId,
    Long orderId,
    PointEarningStatus status,
    Long amount,
    long availableRemainingAmount,
    LocalDateTime confirmedAt,
    LocalDate expiresOn,
    boolean usable,
    LocalDateTime createdAt
) {
    public static PointEarningResponse of(PointEarning earning, LocalDate today) {
        boolean usable = earning.isUsableOn(today) && earning.getRemainingAmount() > 0;
        return new PointEarningResponse(
            earning.getId(),
            earning.getOrderId(),
            earning.getStatus(),
            earning.getAmount(),
            usable ? earning.getRemainingAmount() : 0L,
            earning.getConfirmedAt(),
            earning.getExpiresOn(),
            usable,
            earning.getCreatedAt()
        );
    }
}
