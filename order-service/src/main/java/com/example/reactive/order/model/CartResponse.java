package com.example.reactive.order.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CartResponse(
        String userId,
        List<CartItem> items,
        BigDecimal total,
        String currency,
        Instant updatedAt
) {
}
