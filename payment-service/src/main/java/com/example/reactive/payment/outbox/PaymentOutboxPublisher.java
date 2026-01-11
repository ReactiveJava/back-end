package com.example.reactive.payment.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentOutboxPublisher {
    private final PaymentOutboxService outboxService;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    public void publish() {
        outboxService.publishPendingEvents()
                .doOnError(error -> log.warn("Outbox publish cycle failed", error))
                .subscribe();
    }
}
