package com.example.reactive.bankmock.controller;

import com.example.reactive.bankmock.model.BankPaymentRequest;
import com.example.reactive.bankmock.model.BankPaymentResponse;
import com.example.reactive.bankmock.service.BankMockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/bank")
@Tag(name = "Bank Mock")
@RequiredArgsConstructor
public class BankMockController {
    private final BankMockService bankMockService;

    @PostMapping("/payments")
    @Operation(summary = "Process payment with latency and random failures")
    public Mono<BankPaymentResponse> processPayment(@RequestBody BankPaymentRequest request) {
        return bankMockService.initiatePayment(request);
    }

    @GetMapping(value = "/mock-bank/pay/{sessionId}", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Mock payment form")
    public Mono<String> paymentForm(@PathVariable String sessionId) {
        return Mono.just("""
                <!doctype html>
                <html lang="en">
                  <head>
                    <meta charset="UTF-8"/>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                    <title>Mock Bank</title>
                    <style>
                      body { font-family: Arial, sans-serif; margin: 0; background: #f7f4ee; color: #1f2b2d; }
                      .wrap { max-width: 520px; margin: 10vh auto; padding: 32px; background: #ffffff; border-radius: 24px; box-shadow: 0 24px 60px rgba(0,0,0,0.08); }
                      .badge { display: inline-block; padding: 4px 12px; border-radius: 999px; background: #dff1ef; color: #206662; font-weight: 600; }
                      .btn { margin-top: 16px; padding: 10px 16px; border-radius: 12px; background: #2a837b; color: #fff; display: inline-block; text-decoration: none; }
                    </style>
                  </head>
                  <body>
                    <div class="wrap">
                      <div class="badge">Mock Bank</div>
                      <h2>Processing payment</h2>
                      <p>Session: <strong>%s</strong></p>
                      <p>The payment is being processed. You can close this window and return to the shop.</p>
                      <a class="btn" href="javascript:window.close()">Close window</a>
                    </div>
                  </body>
                </html>
                """.formatted(sessionId));
    }
}
