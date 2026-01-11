package com.example.reactive.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reactive.notification.model.NotificationEvent;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class NotificationServiceIntegrationTest {
    @Autowired
    private WebTestClient webTestClient;

    @Test
    void streamReceivesUserEvent() {
        Flux<ServerSentEvent<NotificationEvent>> stream = webTestClient.get()
                .uri("/api/notifications/stream/{userId}", "user-1")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<NotificationEvent>>() {})
                .getResponseBody();

        NotificationEvent event = new NotificationEvent(
                "user-1",
                "ORDER_CREATED",
                "Order created",
                Map.of("orderId", "order-1"),
                Instant.now()
        );

        StepVerifier.create(stream.filter(sse -> sse.data() != null).take(1))
                .then(() -> webTestClient.post()
                        .uri("/api/notifications/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(event)
                        .exchange()
                        .expectStatus().isOk())
                .assertNext(sse -> {
                    assertThat(sse.event()).isEqualTo("ORDER_CREATED");
                    assertThat(sse.data()).isNotNull();
                    assertThat(sse.data().userId()).isEqualTo("user-1");
                })
                .verifyComplete();
    }

    @Test
    void streamReceivesBroadcastEvent() {
        Flux<ServerSentEvent<NotificationEvent>> stream = webTestClient.get()
                .uri("/api/notifications/stream/{userId}", "user-2")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<NotificationEvent>>() {})
                .getResponseBody();

        NotificationEvent event = new NotificationEvent(
                "all",
                "ALERT",
                "System update",
                Map.of("message", "scheduled"),
                Instant.now()
        );

        StepVerifier.create(stream.filter(sse -> sse.data() != null).take(1))
                .then(() -> webTestClient.post()
                        .uri("/api/notifications/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(event)
                        .exchange()
                        .expectStatus().isOk())
                .assertNext(sse -> {
                    assertThat(sse.event()).isEqualTo("ALERT");
                    assertThat(sse.data()).isNotNull();
                    assertThat(sse.data().userId()).isEqualTo("all");
                })
                .verifyComplete();
    }
}
