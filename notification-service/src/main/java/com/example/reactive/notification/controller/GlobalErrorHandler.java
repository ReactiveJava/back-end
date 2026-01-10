package com.example.reactive.notification.controller;

import com.example.reactive.notification.model.ApiError;
import java.time.Instant;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestControllerAdvice
@Order(-2)
public class GlobalErrorHandler {
    @ExceptionHandler(Throwable.class)
    public Mono<ResponseEntity<ApiError>> handleUnexpected(Throwable ex, ServerWebExchange exchange) {
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Unexpected error",
                exchange.getRequest().getPath().value(),
                exchange.getRequest().getId()
        );
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
    }
}
