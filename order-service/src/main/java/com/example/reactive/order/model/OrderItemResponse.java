package com.example.reactive.order.model;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID productId,
        String name,
        BigDecimal price,
        int quantity
) {
}
