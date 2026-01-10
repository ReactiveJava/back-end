package com.example.reactive.order.model;

import jakarta.validation.constraints.NotBlank;

public record ShippingAddress(
        @NotBlank String fullName,
        @NotBlank String phone,
        @NotBlank String addressLine,
        @NotBlank String city,
        @NotBlank String postalCode
) {
}
