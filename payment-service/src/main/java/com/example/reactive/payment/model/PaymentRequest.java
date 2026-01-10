package com.example.reactive.payment.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PaymentRequest(
        @NotNull UUID orderId,
        @NotBlank String paymentMethod
) {
}
