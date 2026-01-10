package com.example.reactive.order.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductSummary(
        UUID id,
        String name,
        BigDecimal price,
        String currency,
        String imageUrl
) {
}
