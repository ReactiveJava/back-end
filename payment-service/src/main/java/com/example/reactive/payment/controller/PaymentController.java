package com.example.reactive.payment.controller;

import com.example.reactive.payment.model.PaymentRequest;
import com.example.reactive.payment.model.PaymentResponse;
import com.example.reactive.payment.model.PaymentSessionResponse;
import com.example.reactive.payment.model.PaymentStatus;
import com.example.reactive.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Initiate payment")
    public Mono<PaymentSessionResponse> initiate(@Valid @RequestBody PaymentRequest request) {
        return paymentService.initiate(request);
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment by id")
    public Mono<PaymentResponse> getPayment(@PathVariable UUID paymentId) {
        return paymentService.getPayment(paymentId);
    }

    @GetMapping
    @Operation(summary = "List payments")
    public Flux<PaymentResponse> listPayments(
            @Parameter(description = "Filter by order id") @RequestParam(required = false) UUID orderId,
            @Parameter(description = "Filter by status") @RequestParam(required = false) PaymentStatus status) {
        return paymentService.listPayments(orderId, status);
    }
}
