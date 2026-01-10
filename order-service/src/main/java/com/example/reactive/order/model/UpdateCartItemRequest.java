package com.example.reactive.order.model;

import jakarta.validation.constraints.Min;

public record UpdateCartItemRequest(
        @Min(0) int quantity
) {
}
