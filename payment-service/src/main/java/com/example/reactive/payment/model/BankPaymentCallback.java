package com.example.reactive.payment.model;

import java.util.UUID;

public record BankPaymentCallback(
        UUID paymentId,
        String status,
        String reason
) {
}
