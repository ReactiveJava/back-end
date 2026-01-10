package com.example.reactive.payment.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderSnapshot(
        UUID id,
        String userId,
        OrderStatus status,
        BigDecimal total,
        String currency
) {
}
