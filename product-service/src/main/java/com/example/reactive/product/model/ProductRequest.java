package com.example.reactive.product.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank String name,
        String description,
        @NotBlank String category,
        @NotNull @Positive BigDecimal price,
        @NotBlank String currency,
        @NotNull @Min(0) Integer stock,
        String imageUrl
) {
}
