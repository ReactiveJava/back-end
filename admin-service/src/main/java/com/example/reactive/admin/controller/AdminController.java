package com.example.reactive.admin.controller;

import com.example.reactive.admin.client.PaymentClient;
import com.example.reactive.admin.model.AdminEvent;
import com.example.reactive.admin.model.AdminMetrics;
import com.example.reactive.admin.model.PaymentResponse;
import com.example.reactive.admin.model.PaymentStatus;
import com.example.reactive.admin.service.AdminMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminMetricsService metricsService;
    private final PaymentClient paymentClient;

    @PostMapping("/events")
    @Operation(summary = "Ingest admin event")
    public Mono<Void> ingest(@RequestBody AdminEvent event) {
        return metricsService.ingest(event);
    }

    @GetMapping(value = "/metrics/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream admin metrics")
    public Flux<ServerSentEvent<AdminMetrics>> metricsStream() {
        return Flux.interval(Duration.ofSeconds(1))
                .map(tick -> metricsService.snapshot())
                .map(metrics -> ServerSentEvent.builder(metrics)
                        .event("METRICS")
                        .id(metrics.timestamp().toString())
                        .build());
    }

    @GetMapping(value = "/transactions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream payment transactions")
    public Flux<ServerSentEvent<List<PaymentResponse>>> transactionsStream(
            @Parameter(description = "Filter by order id") @RequestParam(required = false) UUID orderId,
            @Parameter(description = "Filter by status") @RequestParam(required = false) PaymentStatus status) {
        Flux<ServerSentEvent<List<PaymentResponse>>> initial = paymentClient.listPayments(orderId, status)
                .collectList()
                .map(list -> ServerSentEvent.builder(list)
                        .event("TRANSACTIONS")
                        .id(Instant.now().toString())
                        .build())
                .flux();

        Flux<ServerSentEvent<List<PaymentResponse>>> updates = metricsService.eventStream()
                .filter(event -> event.type() != null && event.type().startsWith("PAYMENT_"))
                .flatMap(event -> paymentClient.listPayments(orderId, status)
                        .collectList()
                        .map(list -> ServerSentEvent.builder(list)
                                .event("TRANSACTIONS")
                                .id(event.timestamp().toString())
                                .build()));

        return initial.concatWith(updates);
    }

    @GetMapping("/transactions")
    @Operation(summary = "List payment transactions")
    public Flux<PaymentResponse> listPayments(
            @Parameter(description = "Filter by order id") @RequestParam(required = false) UUID orderId,
            @Parameter(description = "Filter by status") @RequestParam(required = false) PaymentStatus status) {
        return paymentClient.listPayments(orderId, status);
    }
}
