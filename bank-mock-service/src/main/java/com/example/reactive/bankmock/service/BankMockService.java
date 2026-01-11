package com.example.reactive.bankmock.service;

import com.example.reactive.bankmock.model.BankPaymentCallback;
import com.example.reactive.bankmock.model.BankPaymentRequest;
import com.example.reactive.bankmock.model.BankPaymentResponse;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Service
public class BankMockService {
    private static final Logger log = LoggerFactory.getLogger(BankMockService.class);

    private final WebClient webClient;
    private final double failureRate;
    private final long minDelayMs;
    private final long maxDelayMs;
    private final Sinks.Many<CallbackTask> callbackSink;

    public BankMockService(
            @Value("${app.bank.failure-rate:0.05}") double failureRate,
            @Value("${app.bank.min-delay-ms:500}") long minDelayMs,
            @Value("${app.bank.max-delay-ms:3000}") long maxDelayMs
    ) {
        this.webClient = WebClient.create();
        this.failureRate = failureRate;
        this.minDelayMs = minDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.callbackSink = Sinks.many().unicast().onBackpressureBuffer();
    }

    @PostConstruct
    void initCallbackProcessor() {
        callbackSink.asFlux()
                .flatMap(task -> Mono.delay(task.delay())
                        .then(sendCallback(task))
                        .onErrorResume(err -> Mono.empty()))
                .subscribe();
    }

    public Mono<BankPaymentResponse> initiatePayment(BankPaymentRequest request) {
        return Mono.defer(() -> {
            String sessionId = UUID.randomUUID().toString();
            String redirectUrl = "http://localhost:8085/api/bank/mock-bank/pay/" + sessionId;
            BankPaymentResponse response = new BankPaymentResponse(sessionId, "PROCESSING", redirectUrl);

            long delay = ThreadLocalRandom.current().nextLong(minDelayMs, maxDelayMs + 1);
            boolean success = ThreadLocalRandom.current().nextDouble() > failureRate;
            String status = success ? "SUCCESS" : "FAILED";
            String reason = success ? null : "Bank rejected the payment";

            log.info("Bank payment scheduled: paymentId={}, orderId={}, status={}, delayMs={}",
                    request.paymentId(), request.orderId(), status, delay);
            callbackSink.tryEmitNext(new CallbackTask(request, status, reason, Duration.ofMillis(delay)));
            return Mono.just(response);
        });
    }

    private Mono<Void> sendCallback(CallbackTask task) {
        return webClient.post()
                .uri(task.request().callbackUrl())
                .bodyValue(new BankPaymentCallback(task.request().paymentId(), task.status(), task.reason()))
                .retrieve()
                .bodyToMono(Void.class);
    }

    private record CallbackTask(BankPaymentRequest request, String status, String reason, Duration delay) {}
}
