package com.example.reactive.payment.model;

public record BankPaymentResponse(
        String sessionId,
        String status,
        String redirectUrl
) {
}
