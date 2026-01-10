package com.example.reactive.bankmock.model;

import java.util.UUID;

public record BankPaymentCallback(
        UUID paymentId,
        String status,
        String reason
) {
}
