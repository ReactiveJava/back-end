package com.example.reactive.order.controller;

import com.example.reactive.order.model.ApiError;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestControllerAdvice
@Order(-2)
public class GlobalErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalErrorHandler.class);

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ApiError>> handleValidation(WebExchangeBindException ex, ServerWebExchange exchange) {
        String message = ex.getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Validation failed");
        return buildError(HttpStatus.BAD_REQUEST, message, exchange);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ApiError>> handleStatus(ResponseStatusException ex, ServerWebExchange exchange) {
        return buildError(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason(), exchange);
    }

    @ExceptionHandler(Throwable.class)
    public Mono<ResponseEntity<ApiError>> handleUnexpected(Throwable ex, ServerWebExchange exchange) {
        log.error("Unhandled error on {}", exchange.getRequest().getPath().value(), ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", exchange);
    }

    private Mono<ResponseEntity<ApiError>> buildError(HttpStatus status, String message, ServerWebExchange exchange) {
        ApiError error = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                exchange.getRequest().getPath().value(),
                exchange.getRequest().getId()
        );
        return Mono.just(ResponseEntity.status(status).body(error));
    }
}
