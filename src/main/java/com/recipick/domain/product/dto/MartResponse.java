package com.recipick.domain.product.dto;


import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.MartStatus;
import com.recipick.domain.product.service.MartService;

import java.math.BigDecimal;

public record MartResponse(
    Long martId,
    Long regionId,
    String name,
    BigDecimal latitude,
    BigDecimal longitude,
    Integer serviceRadiusM,
    MartStatus status
) {

    public static MartResponse from(Mart mart){
        return new MartResponse(
            mart.getId(),
            mart.getRegionId(),
            mart.getName(),
            mart.getLatitude(),
            mart.getLongitude(),
            mart.getServiceRadiusM(),
            mart.getStatus()
        );
    }


}
