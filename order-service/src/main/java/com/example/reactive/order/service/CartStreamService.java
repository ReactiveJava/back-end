package com.example.reactive.order.service;

import com.example.reactive.order.model.CartResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
public class CartStreamService {
    private final Map<String, Sinks.Many<CartResponse>> sinks = new ConcurrentHashMap<>();

    public Flux<ServerSentEvent<CartResponse>> stream(String userId) {
        Sinks.Many<CartResponse> sink = sinks.computeIfAbsent(
                userId,
                ignored -> Sinks.many().multicast().onBackpressureBuffer()
        );
        return sink.asFlux()
                .map(cart -> ServerSentEvent.builder(cart)
                        .event("CART_UPDATED")
                        .id(cart.updatedAt().toString())
                        .build());
    }

    public void emit(CartResponse cart) {
        if (cart == null || cart.userId() == null) {
            return;
        }
        Sinks.Many<CartResponse> sink = sinks.computeIfAbsent(
                cart.userId(),
                ignored -> Sinks.many().multicast().onBackpressureBuffer()
        );
        sink.tryEmitNext(cart);
    }
}
