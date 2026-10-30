package com.recipick.domain.payment.dto.response;

import com.recipick.domain.payment.entity.PointHistory;
import com.recipick.domain.payment.entity.enums.PointHistoryType;

import java.time.LocalDateTime;

// 적립 건 ID와 주문 ID를 함께 내보내 어느 적립 건의 포인트가 어느 주문에서 움직였는지 추적할 수 있게 한다
public record PointHistoryResponse(
    Long historyId,
    Long earningId,
    Long orderId,
    PointHistoryType type,
    Long amount,
    LocalDateTime createdAt
) {
    // 적립 건은 조회할 때 함께 읽어 옴으로(PointHistoryRepository) 이력마다 추가 조회가 생기지 않는다
    public static PointHistoryResponse from(PointHistory history) {
        return new PointHistoryResponse(
            history.getId(),
            history.getPointEarning().getId(),
            history.getOrderId(),
            history.getType(),
            history.getAmount(),
            history.getCreatedAt()
        );
    }
}
