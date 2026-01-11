package com.example.reactive.order.service;

import com.example.reactive.order.client.AdminClient;
import com.example.reactive.order.client.NotificationClient;
import com.example.reactive.order.model.AdminEvent;
import com.example.reactive.order.model.CartResponse;
import com.example.reactive.order.model.CreateOrderRequest;
import com.example.reactive.order.model.NotificationEvent;
import com.example.reactive.order.model.Order;
import com.example.reactive.order.model.OrderItem;
import com.example.reactive.order.model.OrderItemResponse;
import com.example.reactive.order.model.OrderResponse;
import com.example.reactive.order.model.OrderStatus;
import com.example.reactive.order.model.ShippingAddress;
import com.example.reactive.order.model.UpdateOrderStatusRequest;
import com.example.reactive.order.repository.OrderItemRepository;
import com.example.reactive.order.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartService cartService;
    private final NotificationClient notificationClient;
    private final AdminClient adminClient;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        CartService cartService,
                        NotificationClient notificationClient,
                        AdminClient adminClient) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartService = cartService;
        this.notificationClient = notificationClient;
        this.adminClient = adminClient;
    }

    public Mono<OrderResponse> createOrder(CreateOrderRequest request) {
        return cartService.getCart(request.userId())
                .flatMap(cart -> {
                    if (cart.items().isEmpty()) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty"));
                    }
                    Instant now = Instant.now();
                    Order order = new Order(
                            UUID.randomUUID(),
                            request.userId(),
                            OrderStatus.CREATED,
                            cart.total(),
                            cart.currency(),
                            request.paymentMethod(),
                            request.shippingAddress().fullName(),
                            request.shippingAddress().phone(),
                            request.shippingAddress().addressLine(),
                            request.shippingAddress().city(),
                            request.shippingAddress().postalCode(),
                            now,
                            now
                    );
                    return orderRepository.save(order)
                            .flatMap(saved -> Flux.fromIterable(cart.items())
                                    .map(item -> new OrderItem(
                                            UUID.randomUUID(),
                                            saved.getId(),
                                            item.getProductId(),
                                            item.getName(),
                                            item.getPrice(),
                                            item.getQuantity()
                                    ))
                                    .flatMap(orderItemRepository::save)
                                    .then(Mono.just(saved)))
                            .flatMap(saved -> cartService.clearCart(request.userId()).thenReturn(saved));
                })
                .flatMap(saved -> sendCreatedEvents(saved).thenReturn(saved))
                .flatMap(this::toResponse)
                .doOnNext(response -> log.info("Order created: id={}, userId={}, total={}, status={}",
                        response.id(), response.userId(), response.total(), response.status()));
    }

    public Mono<OrderResponse> getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found")))
                .flatMap(this::toResponse);
    }

    public Flux<OrderResponse> getOrdersByUser(String userId) {
        return orderRepository.findByUserId(userId)
                .flatMap(this::toResponse);
    }

    public Mono<OrderResponse> updateStatus(UUID orderId, UpdateOrderStatusRequest request) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found")))
                .flatMap(order -> {
                    order.setStatus(request.status());
                    order.setUpdatedAt(Instant.now());
                    return orderRepository.save(order);
                })
                .flatMap(saved -> sendStatusEvents(saved, request.reason()).thenReturn(saved))
                .flatMap(this::toResponse)
                .doOnNext(response -> log.info("Order status updated: id={}, status={}, reason={}",
                        response.id(), response.status(), request.reason()));
    }

    private Mono<Void> sendCreatedEvents(Order order) {
        NotificationEvent event = new NotificationEvent(
                order.getUserId(),
                "ORDER_CREATED",
                "Order created",
                Map.of("orderId", order.getId(), "status", order.getStatus()),
                Instant.now()
        );
        AdminEvent adminEvent = new AdminEvent("ORDER_CREATED", order.getId(), Instant.now());
        return notificationClient.publish(event).then(adminClient.publish(adminEvent));
    }

    private Mono<Void> sendStatusEvents(Order order, String reason) {
        NotificationEvent event = new NotificationEvent(
                order.getUserId(),
                "ORDER_STATUS",
                reason == null ? "Order status updated" : reason,
                Map.of("orderId", order.getId(), "status", order.getStatus()),
                Instant.now()
        );
        AdminEvent adminEvent = new AdminEvent("ORDER_STATUS", order.getId(), Instant.now());
        return notificationClient.publish(event).then(adminClient.publish(adminEvent));
    }

    private Mono<OrderResponse> toResponse(Order order) {
        Mono<List<OrderItemResponse>> itemsMono = orderItemRepository.findByOrderId(order.getId())
                .map(item -> new OrderItemResponse(item.getId(), item.getProductId(), item.getName(),
                        item.getPrice(), item.getQuantity()))
                .collectList();

        ShippingAddress shipping = new ShippingAddress(
                order.getShippingName(),
                order.getShippingPhone(),
                order.getShippingAddress(),
                order.getShippingCity(),
                order.getShippingPostalCode()
        );

        return itemsMono.map(items -> new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotal(),
                order.getCurrency(),
                order.getPaymentMethod(),
                shipping,
                items,
                order.getCreatedAt(),
                order.getUpdatedAt()
        ));
    }
}
