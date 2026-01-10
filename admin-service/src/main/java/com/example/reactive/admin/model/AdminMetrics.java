package com.example.reactive.admin.model;

import java.time.Instant;

public record AdminMetrics(
        long ordersPerMinute,
        long paymentsSuccess,
        long paymentsFailed,
        double successRate,
        Instant timestamp
) {
}
