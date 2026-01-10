package com.example.reactive.order.model;

import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull OrderStatus status,
        String reason
) {
}
