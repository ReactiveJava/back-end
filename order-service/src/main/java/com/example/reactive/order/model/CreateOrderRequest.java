package com.example.reactive.order.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @NotBlank String userId,
        @NotNull @Valid ShippingAddress shippingAddress,
        @NotBlank String paymentMethod
) {
}
