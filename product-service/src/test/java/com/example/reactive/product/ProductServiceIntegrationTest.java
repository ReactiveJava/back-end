package com.example.reactive.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reactive.product.model.ApiError;
import com.example.reactive.product.model.ProductEvent;
import com.example.reactive.product.model.ProductRequest;
import com.example.reactive.product.model.ProductResponse;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class ProductServiceIntegrationTest {
    private static final UUID SEEDED_ID = UUID.fromString("7e2a9b6d-1b15-4d09-a9a3-9ef7f9a8a001");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("products")
            .withUsername("shop")
            .withPassword("shop");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> String.format(
                "r2dbc:postgresql://%s:%d/%s",
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(5432),
                POSTGRES.getDatabaseName()
        ));
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void listProductsFiltersByCategory() {
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/products")
                        .queryParam("category", "Accessories")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ProductResponse.class)
                .value(list -> {
                    assertThat(list).hasSize(2);
                    assertThat(list).allMatch(product -> "Accessories".equals(product.category()));
                });
    }

    @Test
    void getProductByIdReturnsSeededItem() {
        webTestClient.get()
                .uri("/api/products/{id}", SEEDED_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductResponse.class)
                .value(response -> {
                    assertThat(response.id()).isEqualTo(SEEDED_ID);
                    assertThat(response.category()).isEqualTo("Lighting");
                    assertThat(response.price()).isNotNull();
                });
    }

    @Test
    void createUpdateDeleteProduct() {
        ProductRequest request = new ProductRequest(
                "Desk Lamp",
                "Warm light",
                "Lighting",
                new BigDecimal("89.00"),
                "USD",
                10,
                "https://example.com/lamp.png"
        );

        ProductResponse created = webTestClient.post()
                .uri("/api/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();

        ProductRequest update = new ProductRequest(
                "Desk Lamp Pro",
                "Warm light",
                "Lighting",
                new BigDecimal("99.00"),
                "USD",
                5,
                "https://example.com/lamp-pro.png"
        );

        webTestClient.put()
                .uri("/api/admin/products/{id}", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(update)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductResponse.class)
                .value(updated -> {
                    assertThat(updated.name()).isEqualTo("Desk Lamp Pro");
                    assertThat(updated.price()).isEqualByComparingTo("99.00");
                    assertThat(updated.stock()).isEqualTo(5);
                });

        webTestClient.delete()
                .uri("/api/admin/products/{id}", created.id())
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/api/products/{id}", created.id())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(ApiError.class)
                .value(error -> assertThat(error.message()).isEqualTo("Product not found"));
    }

    @Test
    void validationErrorReturnsApiError() {
        Map<String, Object> payload = Map.of(
                "name", " ",
                "description", "",
                "category", "Lighting",
                "price", 12.50,
                "currency", "USD",
                "stock", 1,
                "imageUrl", "https://example.com/img.png"
        );

        webTestClient.post()
                .uri("/api/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ApiError.class)
                .value(error -> {
                    assertThat(error.status()).isEqualTo(400);
                    assertThat(error.message()).containsAnyOf("must not be blank", "не должно быть пустым");
                });
    }

    @Test
    void streamEmitsProductEvents() {
        Flux<ServerSentEvent<ProductEvent>> stream = webTestClient.get()
                .uri("/api/products/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<ProductEvent>>() {})
                .getResponseBody();

        ProductRequest request = new ProductRequest(
                "SSE Lamp",
                "Streamed",
                "Lighting",
                new BigDecimal("39.00"),
                "USD",
                3,
                "https://example.com/sse.png"
        );

        StepVerifier.create(stream.filter(sse -> sse.data() != null
                        && "SSE Lamp".equals(sse.data().product().name()))
                .take(1))
                .then(() -> webTestClient.post()
                        .uri("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)
                        .exchange()
                        .expectStatus().isOk())
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("PRODUCT_CREATED");
                    assertThat(event.data()).isNotNull();
                    assertThat(event.data().product().name()).isEqualTo("SSE Lamp");
                })
                .verifyComplete();
    }
}
