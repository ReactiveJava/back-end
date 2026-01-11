package com.example.reactive.payment.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "app.outbox")
@Getter
@Setter
public class PaymentOutboxProperties {
    private long pollIntervalMs = 1000;
    private int batchSize = 100;
    private int maxAttempts = 10;
    private long initialBackoffMs = 500;
    private long maxBackoffMs = 10000;
    private int publishConcurrency = 4;
}
