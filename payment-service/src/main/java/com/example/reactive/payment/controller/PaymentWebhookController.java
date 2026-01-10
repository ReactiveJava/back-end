package com.example.reactive.payment.controller;

import com.example.reactive.payment.model.BankPaymentCallback;
import com.example.reactive.payment.model.PaymentResponse;
import com.example.reactive.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/payments/webhook")
@Tag(name = "Payments")
public class PaymentWebhookController {
    private final PaymentService paymentService;

    public PaymentWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @Operation(summary = "Bank callback webhook")
    public Mono<PaymentResponse> handleCallback(@RequestBody BankPaymentCallback callback) {
        return paymentService.handleCallback(callback);
    }
}
