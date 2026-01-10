package com.example.reactive.admin.client;

import com.example.reactive.admin.model.PaymentResponse;
import com.example.reactive.admin.model.PaymentStatus;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Component
public class PaymentClient {
    private final WebClient webClient;

    public PaymentClient(@Value("${app.payments.base-url}") String baseUrl, WebClient.Builder builder) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public Flux<PaymentResponse> listPayments(UUID orderId, PaymentStatus status) {
        return webClient.get()
                .uri(uriBuilder -> {
                    var builderRef = uriBuilder.path("/api/payments");
                    if (orderId != null) {
                        builderRef.queryParam("orderId", orderId);
                    }
                    if (status != null) {
                        builderRef.queryParam("status", status);
                    }
                    return builderRef.build();
                })
                .retrieve()
                .bodyToFlux(PaymentResponse.class);
    }
}
