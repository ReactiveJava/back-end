package com.example.reactive.product.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String description,
        String category,
        BigDecimal price,
        String currency,
        Integer stock,
        String imageUrl,
        Instant updatedAt
) {
}
