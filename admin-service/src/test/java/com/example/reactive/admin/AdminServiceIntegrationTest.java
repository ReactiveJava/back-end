package com.example.reactive.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.reactive.admin.client.PaymentClient;
import com.example.reactive.admin.model.AdminEvent;
import com.example.reactive.admin.model.AdminMetrics;
import com.example.reactive.admin.model.PaymentResponse;
import com.example.reactive.admin.model.PaymentStatus;
import com.example.reactive.admin.service.AdminMetricsService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AdminServiceIntegrationTest {
    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AdminMetricsService metricsService;

    @MockBean
    private PaymentClient paymentClient;

    @BeforeEach
    void setUp() {
        when(paymentClient.listPayments(any(), any())).thenReturn(Flux.empty());
    }

    @Test
    void ingestEventUpdatesMetricsSnapshot() {
        AdminEvent orderEvent = new AdminEvent("ORDER_CREATED", UUID.randomUUID(), Instant.now());
        AdminEvent successEvent = new AdminEvent("PAYMENT_SUCCESS", UUID.randomUUID(), Instant.now());
        AdminEvent failedEvent = new AdminEvent("PAYMENT_FAILED", UUID.randomUUID(), Instant.now());

        webTestClient.post()
                .uri("/api/admin/events")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(orderEvent)
                .exchange()
                .expectStatus().isOk();

        webTestClient.post()
                .uri("/api/admin/events")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(successEvent)
                .exchange()
                .expectStatus().isOk();

        webTestClient.post()
                .uri("/api/admin/events")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(failedEvent)
                .exchange()
                .expectStatus().isOk();

        AdminMetrics metrics = metricsService.snapshot();
        assertThat(metrics.paymentsSuccess()).isEqualTo(1);
        assertThat(metrics.paymentsFailed()).isEqualTo(1);
        assertThat(metrics.ordersPerMinute()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void transactionsListUsesPaymentClient() {
        PaymentResponse response = new PaymentResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user-1",
                new BigDecimal("19.00"),
                "USD",
                PaymentStatus.PAID,
                "CARD",
                "session-1",
                Instant.now(),
                Instant.now()
        );
        when(paymentClient.listPayments(any(), any())).thenReturn(Flux.just(response));

        webTestClient.get()
                .uri("/api/admin/transactions")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(PaymentResponse.class)
                .value(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).status()).isEqualTo(PaymentStatus.PAID);
                });
    }

    @Test
    void transactionsStreamEmitsInitialSnapshot() {
        PaymentResponse response = new PaymentResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user-1",
                new BigDecimal("19.00"),
                "USD",
                PaymentStatus.PAID,
                "CARD",
                "session-1",
                Instant.now(),
                Instant.now()
        );
        when(paymentClient.listPayments(any(), any())).thenReturn(Flux.just(response));

        Flux<ServerSentEvent<List<PaymentResponse>>> stream = webTestClient.get()
                .uri("/api/admin/transactions/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<List<PaymentResponse>>>() {})
                .getResponseBody();

        StepVerifier.create(stream.take(1))
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("TRANSACTIONS");
                    assertThat(event.data()).hasSize(1);
                })
                .verifyComplete();
    }

    @Test
    void metricsStreamEmitsSnapshot() {
        Flux<ServerSentEvent<AdminMetrics>> stream = webTestClient.get()
                .uri("/api/admin/metrics/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<AdminMetrics>>() {})
                .getResponseBody();

        StepVerifier.create(stream.take(1))
                .assertNext(event -> assertThat(event.event()).isEqualTo("METRICS"))
                .verifyComplete();
    }
}
