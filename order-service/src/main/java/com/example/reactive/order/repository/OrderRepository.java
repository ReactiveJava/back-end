package com.example.reactive.order.repository;

import com.example.reactive.order.model.Order;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface OrderRepository extends ReactiveCrudRepository<Order, UUID> {
    Flux<Order> findByUserId(String userId);
}
