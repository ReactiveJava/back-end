package com.example.reactive.order.controller;

import com.example.reactive.order.model.AddCartItemRequest;
import com.example.reactive.order.model.CartResponse;
import com.example.reactive.order.model.UpdateCartItemRequest;
import com.example.reactive.order.service.CartService;
import com.example.reactive.order.service.CartStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/cart")
@Tag(name = "Cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;
    private final CartStreamService cartStreamService;

    @GetMapping("/{userId}")
    @Operation(summary = "Get cart by user id")
    public Mono<CartResponse> getCart(@PathVariable String userId) {
        return cartService.getCart(userId);
    }

    @GetMapping(value = "/stream/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream cart updates")
    public Flux<ServerSentEvent<CartResponse>> stream(@PathVariable String userId) {
        return cartService.getCart(userId)
                .map(cart -> ServerSentEvent.builder(cart)
                        .event("CART_SNAPSHOT")
                        .id(cart.updatedAt().toString())
                        .build())
                .flux()
                .concatWith(cartStreamService.stream(userId));
    }

    @PostMapping("/{userId}/items")
    @Operation(summary = "Add item to cart")
    public Mono<CartResponse> addItem(@PathVariable String userId, @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(userId, request.productId(), request.quantity());
    }

    @PatchMapping("/{userId}/items/{productId}")
    @Operation(summary = "Update item quantity")
    public Mono<CartResponse> updateItem(@PathVariable String userId,
                                         @PathVariable UUID productId,
                                         @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(userId, productId, request.quantity());
    }

    @DeleteMapping("/{userId}/items/{productId}")
    @Operation(summary = "Remove item from cart")
    public Mono<CartResponse> removeItem(@PathVariable String userId, @PathVariable UUID productId) {
        return cartService.removeItem(userId, productId);
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Clear cart")
    public Mono<Void> clearCart(@PathVariable String userId) {
        return cartService.clearCart(userId);
    }
}
