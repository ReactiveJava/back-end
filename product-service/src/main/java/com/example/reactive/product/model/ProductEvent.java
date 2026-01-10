package com.example.reactive.product.model;

import java.time.Instant;

public record ProductEvent(
        String type,
        ProductResponse product,
        Instant timestamp
) {
}
