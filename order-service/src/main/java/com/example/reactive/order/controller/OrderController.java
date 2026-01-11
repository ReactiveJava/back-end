package com.example.reactive.order.controller;

import com.example.reactive.order.model.CreateOrderRequest;
import com.example.reactive.order.model.OrderResponse;
import com.example.reactive.order.model.UpdateOrderStatusRequest;
import com.example.reactive.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Create order from cart")
    public Mono<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by id")
    public Mono<OrderResponse> getOrder(@PathVariable UUID orderId) {
        return orderService.getOrder(orderId);
    }

    @GetMapping
    @Operation(summary = "Get orders by user")
    public Flux<OrderResponse> getOrders(@Parameter(description = "User id") @RequestParam String userId) {
        return orderService.getOrdersByUser(userId);
    }

    @PatchMapping("/{orderId}/status")
    @Operation(summary = "Update order status (internal)")
    public Mono<OrderResponse> updateStatus(@PathVariable UUID orderId,
                                            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateStatus(orderId, request);
    }
}
