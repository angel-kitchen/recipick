package com.recipick.domain.product.dto;

import com.recipick.domain.product.entity.StockAdjustment;
import com.recipick.domain.product.entity.StockAdjustmentType;

import java.time.LocalDateTime;

public record StockAdjustmentHistoryResponse(
    StockAdjustmentType type,
    int quantity,
    int beforeQuantity,
    String reason,
    Long adminId,
    LocalDateTime createdAt
) {

    public static StockAdjustmentHistoryResponse from(StockAdjustment adjustment){
        return new StockAdjustmentHistoryResponse(
            adjustment.getType(),
            adjustment.getQuantity(),
            adjustment.getBeforeQuantity(),
            adjustment.getReason(),
            adjustment.getAdminId(),
            adjustment.getCreatedAt()
        );
    }
}
