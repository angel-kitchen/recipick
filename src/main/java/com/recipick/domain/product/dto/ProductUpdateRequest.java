package com.recipick.domain.product.dto;

import com.recipick.global.common.SaleUnit;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductUpdateRequest(
    @NotNull
    Long categoryId,

    Long ingredientId,

    @NotBlank
    @Size(max = 50)
    String name,

    @Size(min = 1 , max = 50)
    String brand,

    String description,

    @NotNull
    @Digits(integer =  8, fraction = 2)
    BigDecimal saleUnitAmount,

    @NotNull
    SaleUnit saleUnit,

    @Size(max = 255)
    String imageUrl
) {
}
