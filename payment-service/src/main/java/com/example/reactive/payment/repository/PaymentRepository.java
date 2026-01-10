package com.example.reactive.payment.repository;

import com.example.reactive.payment.model.Payment;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface PaymentRepository extends ReactiveCrudRepository<Payment, UUID> {
}
