package com.example.reactive.bankmock.service;

import com.example.reactive.bankmock.model.BankPaymentCallback;
import com.example.reactive.bankmock.model.BankPaymentRequest;
import com.example.reactive.bankmock.model.BankPaymentResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class BankMockService {
    private final WebClient webClient;
    private final double failureRate;
    private final long minDelayMs;
    private final long maxDelayMs;

    public BankMockService(
            @Value("${app.bank.failure-rate:0.05}") double failureRate,
            @Value("${app.bank.min-delay-ms:500}") long minDelayMs,
            @Value("${app.bank.max-delay-ms:3000}") long maxDelayMs
    ) {
        this.webClient = WebClient.create();
        this.failureRate = failureRate;
        this.minDelayMs = minDelayMs;
        this.maxDelayMs = maxDelayMs;
    }

    public Mono<BankPaymentResponse> initiatePayment(BankPaymentRequest request) {
        String sessionId = UUID.randomUUID().toString();
        String redirectUrl = "http://localhost:8085/api/bank/mock-bank/pay/" + sessionId;
        BankPaymentResponse response = new BankPaymentResponse(sessionId, "PROCESSING", redirectUrl);

        long delay = ThreadLocalRandom.current().nextLong(minDelayMs, maxDelayMs + 1);
        boolean success = ThreadLocalRandom.current().nextDouble() > failureRate;
        String status = success ? "SUCCESS" : "FAILED";
        String reason = success ? null : "Bank rejected the payment";

        Mono.delay(Duration.ofMillis(delay))
                .flatMap(ignored -> webClient.post()
                        .uri(request.callbackUrl())
                        .bodyValue(new BankPaymentCallback(request.paymentId(), status, reason))
                        .retrieve()
                        .bodyToMono(Void.class))
                .onErrorResume(err -> Mono.empty())
                .subscribe();

        return Mono.just(response);
    }
}
