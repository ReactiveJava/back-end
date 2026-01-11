package com.example.reactive.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class EndToEndFlowTest {
    @Container
    private static final DockerComposeContainer<?> COMPOSE = new DockerComposeContainer<>(
            new File("../../docker-compose.test.yml")
    )
            .withBuild(true)
            .withExposedService("product-service", 8081, Wait.forHttp("/v3/api-docs").forStatusCode(200))
            .withExposedService("order-service", 8082, Wait.forHttp("/v3/api-docs").forStatusCode(200))
            .withExposedService("payment-service", 8083, Wait.forHttp("/v3/api-docs").forStatusCode(200))
            .withExposedService("notification-service", 8084, Wait.forHttp("/v3/api-docs").forStatusCode(200))
            .withExposedService("bank-mock-service", 8085, Wait.forHttp("/v3/api-docs").forStatusCode(200))
            .withExposedService("admin-service", 8086, Wait.forHttp("/v3/api-docs").forStatusCode(200));

    @Test
    void endToEndHappyPathPaymentFlow() {
        WebTestClient productClient = clientFor("product-service", 8081);
        WebTestClient orderClient = clientFor("order-service", 8082);
        WebTestClient paymentClient = clientFor("payment-service", 8083);

        List<Map<String, Object>> products = productClient.get()
                .uri("/api/products")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(new ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult()
                .getResponseBody();

        assertThat(products).isNotNull();
        assertThat(products).isNotEmpty();

        Map<String, Object> product = products.get(0);
        UUID productId = UUID.fromString(product.get("id").toString());
        BigDecimal price = new BigDecimal(product.get("price").toString());

        String userId = "e2e-user";
        orderClient.post()
                .uri("/api/cart/{userId}/items", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("productId", productId, "quantity", 1))
                .exchange()
                .expectStatus().isOk();

        Map<String, Object> order = orderClient.post()
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "userId", userId,
                        "paymentMethod", "CARD",
                        "shippingAddress", Map.of(
                                "fullName", "E2E User",
                                "phone", "555123",
                                "addressLine", "Main St 1",
                                "city", "Berlin",
                                "postalCode", "10115"
                        )
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult()
                .getResponseBody();

        assertThat(order).isNotNull();
        UUID orderId = UUID.fromString(order.get("id").toString());
        assertThat(order.get("status")).isEqualTo("CREATED");
        assertThat(new BigDecimal(order.get("total").toString())).isEqualByComparingTo(price);

        Map<String, Object> session = paymentClient.post()
                .uri("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("orderId", orderId, "paymentMethod", "CARD"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult()
                .getResponseBody();

        assertThat(session).isNotNull();
        UUID paymentId = UUID.fromString(session.get("paymentId").toString());

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Map<String, Object> payment = paymentClient.get()
                    .uri("/api/payments/{id}", paymentId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .returnResult()
                    .getResponseBody();
            assertThat(payment).isNotNull();
            assertThat(payment.get("status")).isEqualTo("PAID");
        });

        orderClient.get()
                .uri("/api/orders/{id}", orderId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> assertThat(body.get("status")).isEqualTo("PAID"));

        orderClient.get()
                .uri("/api/cart/{userId}", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> assertThat(((List<?>) body.get("items"))).isEmpty());

        paymentClient.post()
                .uri("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("orderId", orderId, "paymentMethod", "CARD"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    private static WebTestClient clientFor(String service, int port) {
        String host = COMPOSE.getServiceHost(service, port);
        Integer mappedPort = COMPOSE.getServicePort(service, port);
        return WebTestClient.bindToServer()
                .responseTimeout(Duration.ofSeconds(10))
                .baseUrl("http://" + host + ":" + mappedPort)
                .build();
    }
}
