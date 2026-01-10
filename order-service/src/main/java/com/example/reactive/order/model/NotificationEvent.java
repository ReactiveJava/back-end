package com.example.reactive.order.model;

import java.time.Instant;
import java.util.Map;

public record NotificationEvent(
        String userId,
        String type,
        String message,
        Map<String, Object> payload,
        Instant timestamp
) {
}
