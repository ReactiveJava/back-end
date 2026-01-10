package com.example.reactive.order.model;

import java.time.Instant;
import java.util.UUID;

public record AdminEvent(
        String type,
        UUID orderId,
        Instant timestamp
) {
}
