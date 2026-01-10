package com.example.reactive.bankmock.model;

public record BankPaymentResponse(
        String sessionId,
        String status,
        String redirectUrl
) {
}
