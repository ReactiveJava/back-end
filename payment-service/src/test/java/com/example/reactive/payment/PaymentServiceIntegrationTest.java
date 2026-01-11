package com.example.reactive.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reactive.payment.model.ApiError;
import com.example.reactive.payment.model.BankPaymentCallback;
import com.example.reactive.payment.model.OrderStatus;
import com.example.reactive.payment.model.Payment;
import com.example.reactive.payment.model.PaymentRequest;
import com.example.reactive.payment.model.PaymentResponse;
import com.example.reactive.payment.model.PaymentSessionResponse;
import com.example.reactive.payment.model.PaymentStatus;
import com.example.reactive.payment.repository.PaymentRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class PaymentServiceIntegrationTest {
    private static final UUID ORDER_ID = UUID.fromString("2eb0bd2c-68d4-4bd8-9ae4-8e2b2b988fc6");
    private static final UUID PAYMENT_ID = UUID.fromString("4a5e0e3c-7e8e-4ce7-9a18-12ae6c812eb9");
    private static final MockWebServer MOCK_WEB_SERVER = new MockWebServer();
    private static final AtomicReference<OrderStatus> ORDER_STATUS = new AtomicReference<>(OrderStatus.CREATED);
    private static final AtomicReference<OrderStatus> UPDATED_ORDER_STATUS = new AtomicReference<>();
    private static final AtomicInteger ADMIN_EVENTS = new AtomicInteger();
    private static final AtomicInteger NOTIFICATION_EVENTS = new AtomicInteger();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("payments")
            .withUsername("shop")
            .withPassword("shop");

    static {
        try {
            MOCK_WEB_SERVER.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    String path = request.getPath();
                    if (path != null && path.contains("/status") && "PATCH".equals(request.getMethod())) {
                        String body = request.getBody().readUtf8();
                        if (body.contains("FAILED")) {
                            UPDATED_ORDER_STATUS.set(OrderStatus.FAILED);
                        } else if (body.contains("PAID")) {
                            UPDATED_ORDER_STATUS.set(OrderStatus.PAID);
                        }
                        return new MockResponse().setResponseCode(200);
                    }
                    if (path != null && path.startsWith("/api/orders/")) {
                        return new MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(orderJson());
                    }
                    if (path != null && path.startsWith("/api/bank/payments")) {
                        return new MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody("{\"sessionId\":\"session-1\",\"status\":\"PROCESSING\"," +
                                        "\"redirectUrl\":\"https://bank.example/redirect\"}");
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
        registry.add("app.bank.callback-url", () -> "http://localhost/callback");
        registry.add("app.bank.base-url", () -> MOCK_WEB_SERVER.url("/").toString());
        registry.add("app.orders.base-url", () -> MOCK_WEB_SERVER.url("/").toString());
        registry.add("app.notifications.base-url", () -> MOCK_WEB_SERVER.url("/").toString());
        registry.add("app.admin.base-url", () -> MOCK_WEB_SERVER.url("/").toString());
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PaymentRepository paymentRepository;

    @AfterAll
    static void tearDown() throws IOException {
        MOCK_WEB_SERVER.shutdown();
    }

    @BeforeEach
    void resetState() {
        StepVerifier.create(paymentRepository.deleteAll()).verifyComplete();
        ORDER_STATUS.set(OrderStatus.CREATED);
        UPDATED_ORDER_STATUS.set(null);
        ADMIN_EVENTS.set(0);
        NOTIFICATION_EVENTS.set(0);
    }

    @Test
    void initiatePaymentReturnsSession() {
        PaymentSessionResponse response = webTestClient.post()
                .uri("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PaymentRequest(ORDER_ID, "CARD"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentSessionResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(response.redirectUrl()).contains("bank.example");

        StepVerifier.create(paymentRepository.findById(response.paymentId()))
                .assertNext(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
                    assertThat(saved.getProviderSessionId()).isEqualTo("session-1");
                })
                .verifyComplete();
        assertThat(ADMIN_EVENTS.get()).isEqualTo(1);
    }

    @Test
    void initiatePaymentFailsWhenOrderAlreadyProcessed() {
        ORDER_STATUS.set(OrderStatus.PAID);

        webTestClient.post()
                .uri("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PaymentRequest(ORDER_ID, "CARD"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ApiError.class)
                .value(error -> assertThat(error.message()).isEqualTo("Order already processed"));
    }

    @Test
    void handleCallbackUpdatesPaymentAndSendsEvents() {
        Payment payment = new Payment(
                PAYMENT_ID,
                ORDER_ID,
                "user-1",
                new BigDecimal("49.00"),
                "USD",
                PaymentStatus.INITIATED,
                "CARD",
                null,
                Instant.now(),
                Instant.now()
        );
        StepVerifier.create(paymentRepository.save(payment))
                .assertNext(saved -> assertThat(saved.getId()).isEqualTo(PAYMENT_ID))
                .verifyComplete();

        PaymentResponse response = webTestClient.post()
                .uri("/api/payments/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new BankPaymentCallback(PAYMENT_ID, "SUCCESS", null))
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(UPDATED_ORDER_STATUS.get()).isEqualTo(OrderStatus.PAID);
        assertThat(NOTIFICATION_EVENTS.get()).isEqualTo(1);
        assertThat(ADMIN_EVENTS.get()).isEqualTo(1);
    }

    @Test
    void handleCallbackFailureMarksPaymentFailed() {
        Payment payment = new Payment(
                UUID.randomUUID(),
                ORDER_ID,
                "user-2",
                new BigDecimal("49.00"),
                "USD",
                PaymentStatus.PROCESSING,
                "CARD",
                null,
                Instant.now(),
                Instant.now()
        );
        StepVerifier.create(paymentRepository.save(payment))
                .assertNext(saved -> assertThat(saved.getId()).isEqualTo(payment.getId()))
                .verifyComplete();

        PaymentResponse response = webTestClient.post()
                .uri("/api/payments/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new BankPaymentCallback(payment.getId(), "FAILED", "Declined"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(UPDATED_ORDER_STATUS.get()).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    void getPaymentNotFoundReturnsApiError() {
        webTestClient.get()
                .uri("/api/payments/{id}", UUID.randomUUID())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(ApiError.class)
                .value(error -> assertThat(error.message()).isEqualTo("Payment not found"));
    }

    @Test
    void listPaymentsWithFilters() {
        Payment first = new Payment(
                UUID.randomUUID(),
                ORDER_ID,
                "user-1",
                new BigDecimal("20.00"),
                "USD",
                PaymentStatus.PAID,
                "CARD",
                "session-a",
                Instant.now(),
                Instant.now()
        );
        Payment second = new Payment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user-2",
                new BigDecimal("25.00"),
                "USD",
                PaymentStatus.FAILED,
                "CARD",
                "session-b",
                Instant.now(),
                Instant.now()
        );
        StepVerifier.create(paymentRepository.save(first).then(paymentRepository.save(second)).then())
                .verifyComplete();

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/payments")
                        .queryParam("orderId", ORDER_ID)
                        .queryParam("status", PaymentStatus.PAID)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(PaymentResponse.class)
                .value(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).status()).isEqualTo(PaymentStatus.PAID);
                });
    }

    private static String orderJson() {
        return String.format(
                "{\"id\":\"%s\",\"userId\":\"user-1\",\"status\":\"%s\","
                        + "\"total\":49.00,\"currency\":\"USD\"}",
                ORDER_ID,
                ORDER_STATUS.get()
        );
    }
}
