package com.example.reactive.payment.model;

public record UpdateOrderStatusRequest(
        OrderStatus status,
        String reason
) {
}
