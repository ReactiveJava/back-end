package com.example.reactive.order.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String userId,
        OrderStatus status,
        BigDecimal total,
        String currency,
        String paymentMethod,
        ShippingAddress shippingAddress,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
