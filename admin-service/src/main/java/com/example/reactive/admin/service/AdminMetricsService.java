package com.example.reactive.admin.service;

import com.example.reactive.admin.model.AdminEvent;
import com.example.reactive.admin.model.AdminMetrics;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.LongAdder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Service
@Slf4j
public class AdminMetricsService {
    private final Deque<Instant> orderEvents = new ConcurrentLinkedDeque<>();
    private final LongAdder paymentsSuccess = new LongAdder();
    private final LongAdder paymentsFailed = new LongAdder();
    private final Sinks.Many<AdminEvent> eventSink = Sinks.many().multicast().onBackpressureBuffer();

    public Mono<Void> ingest(AdminEvent event) {
        return Mono.fromRunnable(() -> {
            if (event == null) {
                return;
            }
            log.info("Admin event ingested: type={}, orderId={}", event.type(), event.orderId());
            if ("ORDER_CREATED".equalsIgnoreCase(event.type())) {
                orderEvents.add(event.timestamp());
            }
            if ("PAYMENT_SUCCESS".equalsIgnoreCase(event.type())) {
                paymentsSuccess.increment();
            }
            if ("PAYMENT_FAILED".equalsIgnoreCase(event.type())) {
                paymentsFailed.increment();
            }
            eventSink.tryEmitNext(event);
        });
    }

    public Flux<AdminEvent> eventStream() {
        return eventSink.asFlux();
    }

    public AdminMetrics snapshot() {
        long ordersPerMinute = pruneAndCount(orderEvents);
        long success = paymentsSuccess.sum();
        long failed = paymentsFailed.sum();
        long totalPayments = success + failed;
        double successRate = totalPayments == 0 ? 0.0 : (double) success / totalPayments;
        return new AdminMetrics(ordersPerMinute, success, failed, successRate, Instant.now());
    }

    private long pruneAndCount(Deque<Instant> events) {
        Instant cutoff = Instant.now().minusSeconds(60);
        while (true) {
            Instant head = events.peekFirst();
            if (head == null || !head.isBefore(cutoff)) {
                break;
            }
            events.pollFirst();
        }
        return events.size();
    }
}
