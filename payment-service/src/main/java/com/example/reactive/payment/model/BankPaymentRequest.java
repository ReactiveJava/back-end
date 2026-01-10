package com.example.reactive.payment.model;

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
