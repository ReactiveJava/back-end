package com.example.reactive.payment.client;

import com.example.reactive.payment.model.AdminEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class AdminClient {
    private final WebClient webClient;

    public AdminClient(@Value("${app.admin.base-url}") String baseUrl, WebClient.Builder builder) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public Mono<Void> publish(AdminEvent event) {
        return webClient.post()
                .uri("/api/admin/events")
                .bodyValue(event)
                .retrieve()
                .bodyToMono(Void.class);
    }
}
