package com.example.reactive.payment.outbox;

import com.example.reactive.payment.client.AdminClient;
import com.example.reactive.payment.client.NotificationClient;
import com.example.reactive.payment.model.AdminEvent;
import com.example.reactive.payment.model.NotificationEvent;
import com.example.reactive.payment.model.Payment;
import com.example.reactive.payment.model.PaymentStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOutboxService {
    private final PaymentOutboxRepository repository;
    private final PaymentOutboxProperties properties;
    private final ObjectMapper objectMapper;
    private final NotificationClient notificationClient;
    private final AdminClient adminClient;

    public Mono<Void> enqueuePaymentInitiated(Payment payment) {
        AdminEvent adminEvent = new AdminEvent("PAYMENT_INITIATED", payment.getOrderId(), Instant.now());
        return enqueueEvent(payment, OutboxTarget.ADMIN, adminEvent.type(), adminEvent);
    }

    public Mono<Void> enqueuePaymentResult(Payment payment, PaymentStatus status, String reason) {
        String message = status == PaymentStatus.PAID ? "Payment successful" : "Payment failed";
        NotificationEvent notificationEvent = new NotificationEvent(
                payment.getUserId(),
                "PAYMENT_" + status.name(),
                reason == null ? message : reason,
                Map.of("paymentId", payment.getId(), "orderId", payment.getOrderId(), "status", status),
                Instant.now()
        );
        String adminType = status == PaymentStatus.PAID ? "PAYMENT_SUCCESS" : "PAYMENT_FAILED";
        AdminEvent adminEvent = new AdminEvent(adminType, payment.getOrderId(), Instant.now());
        return Mono.when(
                enqueueEvent(payment, OutboxTarget.NOTIFICATION, notificationEvent.type(), notificationEvent),
                enqueueEvent(payment, OutboxTarget.ADMIN, adminEvent.type(), adminEvent)
        ).then();
    }

    public Mono<Void> publishPendingEvents() {
        Instant now = Instant.now();
        return repository.findReadyEvents(properties.getBatchSize(), properties.getMaxAttempts(), now)
                .flatMap(event -> claimAndPublish(event, now), properties.getPublishConcurrency())
                .then();
    }

    private Mono<Void> enqueueEvent(Payment payment, OutboxTarget target, String eventType, Object payload) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(json -> {
                    PaymentOutboxEvent event = new PaymentOutboxEvent(
                            UUID.randomUUID(),
                            payment.getId(),
                            payment.getOrderId(),
                            target,
                            eventType,
                            json,
                            OutboxStatus.PENDING,
                            0,
                            Instant.now(),
                            null,
                            null,
                            Instant.now()
                    );
                    return repository.insertIgnoreDuplicate(event);
                });
    }

    private Mono<Void> claimAndPublish(PaymentOutboxEvent event, Instant now) {
        Instant nextAttemptAt = nextAttemptAt(event.getAttempts() + 1);
        return repository.claimEvent(event.getId(), now, nextAttemptAt)
                .flatMap(claimed -> {
                    if (!claimed) {
                        return Mono.empty();
                    }
                    return dispatchEvent(event)
                            .then(repository.markSent(event.getId(), Instant.now()))
                            .onErrorResume(error -> {
                                log.warn("Failed to publish outbox event: id={}, target={}, type={}",
                                        event.getId(), event.getTarget(), event.getEventType(), error);
                                return repository.markFailed(event.getId(), nextAttemptAt);
                            });
                });
    }

    private Mono<Void> dispatchEvent(PaymentOutboxEvent event) {
        return switch (event.getTarget()) {
            case ADMIN -> adminClient.publishRaw(event.getPayload());
            case NOTIFICATION -> notificationClient.publishRaw(event.getPayload());
        };
    }

    private Instant nextAttemptAt(int attempts) {
        int exponent = Math.min(attempts - 1, 10);
        long backoff = properties.getInitialBackoffMs() * (1L << Math.max(exponent, 0));
        long bounded = Math.min(backoff, properties.getMaxBackoffMs());
        return Instant.now().plusMillis(bounded);
    }
}
