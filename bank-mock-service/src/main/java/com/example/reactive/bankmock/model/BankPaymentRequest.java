package com.example.reactive.bankmock.model;

import java.math.BigDecimal;
import java.util.UUID;

public record BankPaymentRequest(
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        String currency,
        String callbackUrl
) {
}
