package com.recipick.domain.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MartCreateRequest(
    @NotNull
    Long regionId,

    @NotBlank
    @Size(max = 50)
    String name,

    @NotNull
    BigDecimal latitude,
    @NotNull
    BigDecimal longitude,

    Integer serviceRadiusM
) {
}
