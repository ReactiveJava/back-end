package com.example.reactive.payment.model;

import java.util.UUID;

public record PaymentSessionResponse(
        UUID paymentId,
        PaymentStatus status,
        String redirectUrl
) {
}
