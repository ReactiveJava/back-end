package com.example.reactive.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reactive.order.model.AddCartItemRequest;
import com.example.reactive.order.model.ApiError;
import com.example.reactive.order.model.CartResponse;
import com.example.reactive.order.model.CreateOrderRequest;
import com.example.reactive.order.model.OrderResponse;
import com.example.reactive.order.model.OrderStatus;
import com.example.reactive.order.model.ShippingAddress;
import com.example.reactive.order.model.UpdateCartItemRequest;
import com.example.reactive.order.model.UpdateOrderStatusRequest;
import com.example.reactive.order.repository.OrderItemRepository;
import com.example.reactive.order.repository.OrderRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class OrderServiceIntegrationTest {
    private static final UUID PRODUCT_ID = UUID.fromString("c0a8012b-7f75-4c3d-8cb4-1fdb3bd91ea1");
    private static final BigDecimal PRODUCT_PRICE = new BigDecimal("19.99");
    private static final MockWebServer MOCK_WEB_SERVER = new MockWebServer();
    private static final AtomicInteger ADMIN_EVENTS = new AtomicInteger();
    private static final AtomicInteger NOTIFICATION_EVENTS = new AtomicInteger();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("orders")
            .withUsername("shop")
            .withPassword("shop");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static {
        try {
            MOCK_WEB_SERVER.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    String path = request.getPath();
                    if (path != null && path.startsWith("/api/products/")) {
                        return new MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(productJson());
                    }
                    if ("/api/notifications/events".equals(path)) {
                        NOTIFICATION_EVENTS.incrementAndGet();
                        return new MockResponse().setResponseCode(200);
                    }
                    if ("/api/admin/events".equals(path)) {
                        ADMIN_EVENTS.incrementAndGet();
                        return new MockResponse().setResponseCode(200);
                    }
                    return new MockResponse().setResponseCode(404);
                }
            });
            MOCK_WEB_SERVER.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

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
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.products.base-url", () -> MOCK_WEB_SERVER.url("/").toString());
        registry.add("app.notifications.base-url", () -> MOCK_WEB_SERVER.url("/").toString());
        registry.add("app.admin.base-url", () -> MOCK_WEB_SERVER.url("/").toString());
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ReactiveRedisConnectionFactory redisConnectionFactory;

    @AfterAll
    static void tearDown() throws IOException {
        MOCK_WEB_SERVER.shutdown();
    }

    @BeforeEach
    void resetState() {
        StepVerifier.create(orderItemRepository.deleteAll().then(orderRepository.deleteAll())).verifyComplete();
        StepVerifier.create(redisConnectionFactory.getReactiveConnection()
                .serverCommands()
                .flushAll()
                .then())
                .verifyComplete();
        ADMIN_EVENTS.set(0);
        NOTIFICATION_EVENTS.set(0);
    }

    @Test
    void getCartReturnsEmptyForNewUser() {
        webTestClient.get()
                .uri("/api/cart/{userId}", "user-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody(CartResponse.class)
                .value(cart -> {
                    assertThat(cart.items()).isEmpty();
                    assertThat(cart.total()).isEqualByComparingTo("0");
                    assertThat(cart.currency()).isEqualTo("USD");
                });
    }

    @Test
    void addUpdateRemoveCartItems() {
        CartResponse added = webTestClient.post()
                .uri("/api/cart/{userId}/items", "user-2")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AddCartItemRequest(PRODUCT_ID, 2))
                .exchange()
                .expectStatus().isOk()
                .expectBody(CartResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(added).isNotNull();
        assertThat(added.items()).hasSize(1);
        assertThat(added.total()).isEqualByComparingTo(PRODUCT_PRICE.multiply(BigDecimal.valueOf(2)));

        webTestClient.patch()
                .uri("/api/cart/{userId}/items/{productId}", "user-2", PRODUCT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateCartItemRequest(0))
                .exchange()
                .expectStatus().isOk()
                .expectBody(CartResponse.class)
                .value(cart -> assertThat(cart.items()).isEmpty());

        webTestClient.post()
                .uri("/api/cart/{userId}/items", "user-2")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AddCartItemRequest(PRODUCT_ID, 1))
                .exchange()
                .expectStatus().isOk();

        webTestClient.delete()
                .uri("/api/cart/{userId}/items/{productId}", "user-2", PRODUCT_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody(CartResponse.class)
                .value(cart -> assertThat(cart.items()).isEmpty());

        webTestClient.delete()
                .uri("/api/cart/{userId}", "user-2")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void streamEmitsSnapshotAndUpdates() {
        Flux<ServerSentEvent<CartResponse>> stream = webTestClient.get()
                .uri("/api/cart/stream/{userId}", "user-3")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<CartResponse>>() {})
                .getResponseBody();

        StepVerifier.create(stream.take(2))
                .then(() -> webTestClient.post()
                        .uri("/api/cart/{userId}/items", "user-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new AddCartItemRequest(PRODUCT_ID, 1))
                        .exchange()
                        .expectStatus().isOk())
                .assertNext(event -> assertThat(event.event()).isEqualTo("CART_SNAPSHOT"))
                .assertNext(event -> assertThat(event.event()).isEqualTo("CART_UPDATED"))
                .verifyComplete();
    }

    @Test
    void createOrderFromCartClearsCartAndEmitsEvents() {
        webTestClient.post()
                .uri("/api/cart/{userId}/items", "user-4")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AddCartItemRequest(PRODUCT_ID, 1))
                .exchange()
                .expectStatus().isOk();

        CreateOrderRequest request = new CreateOrderRequest(
                "user-4",
                new ShippingAddress("Jane Doe", "123456", "Main St 1", "Berlin", "10115"),
                "CARD"
        );

        OrderResponse created = webTestClient.post()
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(created.items()).hasSize(1);
        assertThat(created.total()).isEqualByComparingTo(PRODUCT_PRICE);

        webTestClient.get()
                .uri("/api/cart/{userId}", "user-4")
                .exchange()
                .expectStatus().isOk()
                .expectBody(CartResponse.class)
                .value(cart -> assertThat(cart.items()).isEmpty());

        assertThat(ADMIN_EVENTS.get()).isEqualTo(1);
        assertThat(NOTIFICATION_EVENTS.get()).isEqualTo(1);
    }

    @Test
    void createOrderFailsWhenCartIsEmpty() {
        CreateOrderRequest request = new CreateOrderRequest(
                "user-5",
                new ShippingAddress("Jane Doe", "123456", "Main St 1", "Berlin", "10115"),
                "CARD"
        );

        webTestClient.post()
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ApiError.class)
                .value(error -> assertThat(error.message()).isEqualTo("Cart is empty"));
    }

    @Test
    void getAndUpdateOrder() {
        webTestClient.post()
                .uri("/api/cart/{userId}/items", "user-6")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AddCartItemRequest(PRODUCT_ID, 1))
                .exchange()
                .expectStatus().isOk();

        CreateOrderRequest request = new CreateOrderRequest(
                "user-6",
                new ShippingAddress("Jane Doe", "123456", "Main St 1", "Berlin", "10115"),
                "CARD"
        );

        OrderResponse created = webTestClient.post()
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();

        webTestClient.get()
                .uri("/api/orders/{id}", created.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderResponse.class)
                .value(order -> assertThat(order.id()).isEqualTo(created.id()));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/orders").queryParam("userId", "user-6").build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(OrderResponse.class)
                .value(list -> assertThat(list).hasSize(1));

        webTestClient.patch()
                .uri("/api/orders/{id}/status", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateOrderStatusRequest(OrderStatus.PAID, "Payment received"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderResponse.class)
                .value(order -> assertThat(order.status()).isEqualTo(OrderStatus.PAID));
    }

    @Test
    void getOrderNotFoundReturnsApiError() {
        webTestClient.get()
                .uri("/api/orders/{id}", UUID.randomUUID())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(ApiError.class)
                .value(error -> assertThat(error.message()).isEqualTo("Order not found"));
    }

    private static String productJson() {
        return String.format(
                "{\"id\":\"%s\",\"name\":\"Test Product\",\"price\":%s,"
                        + "\"currency\":\"USD\",\"imageUrl\":\"https://example.com/product.png\"}",
                PRODUCT_ID,
                PRODUCT_PRICE.toPlainString()
        );
    }
}
