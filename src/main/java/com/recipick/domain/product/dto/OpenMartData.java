package com.recipick.domain.product.dto;

import com.recipick.domain.product.entity.Mart;

public record OpenMartData(
    Long martId,
    String name,
    Long regionId
) {

    public static OpenMartData from(Mart mart) {
        return new OpenMartData(
            mart.getId(),
            mart.getName(),
            mart.getRegionId()
        );
    }
}
