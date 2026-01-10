package com.example.reactive.notification.model;

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
