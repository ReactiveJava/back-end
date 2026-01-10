package com.example.reactive.order.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddCartItemRequest(
        @NotNull UUID productId,
        @Min(1) int quantity
) {
}
