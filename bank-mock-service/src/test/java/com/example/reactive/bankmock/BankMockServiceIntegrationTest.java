package com.example.reactive.bankmock;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reactive.bankmock.model.BankPaymentRequest;
import com.example.reactive.bankmock.model.BankPaymentResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.bank.failure-rate=0",
                "app.bank.min-delay-ms=0",
                "app.bank.max-delay-ms=0"
        }
)
@AutoConfigureWebTestClient
class BankMockServiceIntegrationTest {
    private static MockWebServer callbackServer;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void startCallbackServer() throws IOException {
        callbackServer = new MockWebServer();
        callbackServer.start();
    }

    @AfterAll
    static void stopCallbackServer() throws IOException {
        callbackServer.shutdown();
    }

    @Test
    void processPaymentReturnsResponseAndCallsCallback() throws InterruptedException {
        String callbackUrl = callbackServer.url("/callback").toString();
        BankPaymentRequest request = new BankPaymentRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("99.00"),
                "USD",
                callbackUrl
        );

        BankPaymentResponse response = webTestClient.post()
                .uri("/api/bank/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(BankPaymentResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("PROCESSING");
        assertThat(response.redirectUrl()).contains("/api/bank/mock-bank/pay/");

        RecordedRequest recorded = callbackServer.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getPath()).isEqualTo("/callback");
    }

    @Test
    void paymentFormContainsSessionId() {
        String sessionId = "session-test";
        webTestClient.get()
                .uri("/api/bank/mock-bank/pay/{sessionId}", sessionId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains(sessionId));
    }
}
