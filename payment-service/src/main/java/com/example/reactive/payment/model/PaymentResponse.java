package com.example.reactive.payment.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        String userId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String provider,
        String providerSessionId,
        Instant createdAt,
        Instant updatedAt
) {
}
