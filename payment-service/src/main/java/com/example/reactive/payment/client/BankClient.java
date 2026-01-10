package com.example.reactive.payment.client;

import com.example.reactive.payment.model.BankPaymentRequest;
import com.example.reactive.payment.model.BankPaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class BankClient {
    private final WebClient webClient;

    public BankClient(@Value("${app.bank.base-url}") String baseUrl, WebClient.Builder builder) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public Mono<BankPaymentResponse> initiatePayment(BankPaymentRequest request) {
        return webClient.post()
                .uri("/api/bank/payments")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(BankPaymentResponse.class);
    }
}
