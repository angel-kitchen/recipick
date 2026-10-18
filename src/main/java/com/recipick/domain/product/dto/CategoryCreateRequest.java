package com.recipick.domain.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(
  @NotBlank
  @Size(max =50)
  String name
)  {
}
