package com.recipick.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentConfirmRequest(
    @NotNull Long orderId,
    @NotBlank String portonePaymentId
) {
}
