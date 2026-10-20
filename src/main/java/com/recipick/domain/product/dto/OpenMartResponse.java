package com.recipick.domain.product.dto;

import com.recipick.domain.product.entity.Mart;

public record OpenMartResponse(
    Long martId,
    String name,
    Long regionId
) {

    public static OpenMartResponse from(Mart mart){
        return new OpenMartResponse(
            mart.getId(),
            mart.getName(),
            mart.getRegionId()
        );
    }
}
