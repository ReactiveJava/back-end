package com.example.reactive.payment.client;

import com.example.reactive.payment.model.OrderSnapshot;
import com.example.reactive.payment.model.OrderStatus;
import com.example.reactive.payment.model.UpdateOrderStatusRequest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class OrderClient {
    private final WebClient webClient;

    public OrderClient(@Value("${app.orders.base-url}") String baseUrl, WebClient.Builder builder) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public Mono<OrderSnapshot> getOrder(UUID orderId) {
        return webClient.get()
                .uri("/api/orders/{id}", orderId)
                .retrieve()
                .bodyToMono(OrderSnapshot.class);
    }

    public Mono<Void> updateStatus(UUID orderId, OrderStatus status, String reason) {
        return webClient.patch()
                .uri("/api/orders/{id}/status", orderId)
                .bodyValue(new UpdateOrderStatusRequest(status, reason))
                .retrieve()
                .bodyToMono(Void.class);
    }
}
