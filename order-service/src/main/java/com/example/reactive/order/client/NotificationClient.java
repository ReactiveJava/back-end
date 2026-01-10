package com.example.reactive.order.client;

import com.example.reactive.order.model.NotificationEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class NotificationClient {
    private final WebClient webClient;

    public NotificationClient(@Value("${app.notifications.base-url}") String baseUrl, WebClient.Builder builder) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public Mono<Void> publish(NotificationEvent event) {
        return webClient.post()
                .uri("/api/notifications/events")
                .bodyValue(event)
                .retrieve()
                .bodyToMono(Void.class);
    }
}
