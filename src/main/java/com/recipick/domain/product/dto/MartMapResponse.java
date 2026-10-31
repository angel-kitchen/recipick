package com.recipick.domain.product.dto;

import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.MartStatus;

import java.math.BigDecimal;

public record MartMapResponse(
    Long martId,
    String name,
    BigDecimal latitude,
    BigDecimal longitude,
    Integer serviceRadiusM,
    MartStatus status
) {

    public static MartMapResponse from(Mart mart){
        return new MartMapResponse(
            mart.getId(),
            mart.getName(),
            mart.getLatitude(),
            mart.getLongitude(),
            mart.getServiceRadiusM(),
            mart.getStatus()
        );
    }
}
