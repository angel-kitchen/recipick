package com.recipick.infra.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;


@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneWebhook(
    String type,
    OffsetDateTime timestamp,
    Data data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
        String storeId,
        String paymentId,
        String transactionId,
        String cancellationId
    ) {
    }
}
