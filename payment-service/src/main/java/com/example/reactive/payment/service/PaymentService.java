package com.example.reactive.payment.service;

import com.example.reactive.payment.client.BankClient;
import com.example.reactive.payment.client.OrderClient;
import com.example.reactive.payment.model.BankPaymentCallback;
import com.example.reactive.payment.model.BankPaymentRequest;
import com.example.reactive.payment.model.OrderStatus;
import com.example.reactive.payment.model.Payment;
import com.example.reactive.payment.model.PaymentRequest;
import com.example.reactive.payment.model.PaymentResponse;
import com.example.reactive.payment.model.PaymentSessionResponse;
import com.example.reactive.payment.model.PaymentStatus;
import com.example.reactive.payment.repository.PaymentRepository;
import com.example.reactive.payment.outbox.PaymentOutboxService;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class PaymentService {
    private final PaymentRepository repository;
    private final R2dbcEntityTemplate template;
    private final OrderClient orderClient;
    private final BankClient bankClient;
    private final PaymentOutboxService outboxService;
    private final String callbackUrl;
    private final TransactionalOperator transactionalOperator;

    public PaymentService(PaymentRepository repository,
                          R2dbcEntityTemplate template,
                          OrderClient orderClient,
                          BankClient bankClient,
                          PaymentOutboxService outboxService,
                          ReactiveTransactionManager transactionManager,
                          @Value("${app.bank.callback-url}") String callbackUrl) {
        this.repository = repository;
        this.template = template;
        this.orderClient = orderClient;
        this.bankClient = bankClient;
        this.outboxService = outboxService;
        this.callbackUrl = callbackUrl;
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
    }

    public Mono<PaymentSessionResponse> initiate(PaymentRequest request) {
        return orderClient.getOrder(request.orderId())
                .flatMap(order -> {
                    if (order.status() != OrderStatus.CREATED) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order already processed"));
                    }
                    Payment payment = new Payment(
                            UUID.randomUUID(),
                            order.id(),
                            order.userId(),
                            order.total(),
                            order.currency(),
                            PaymentStatus.INITIATED,
                            request.paymentMethod(),
                            null,
                            Instant.now(),
                            Instant.now()
                    );
                    Mono<Payment> persisted = repository.save(payment)
                            .doOnNext(saved -> log.info(
                                    "Payment initiated: paymentId={}, orderId={}, userId={}, amount={}, currency={}, method={}",
                                    saved.getId(), saved.getOrderId(), saved.getUserId(),
                                    saved.getAmount(), saved.getCurrency(), saved.getProvider()))
                            .flatMap(saved -> outboxService.enqueuePaymentInitiated(saved).thenReturn(saved));
                    return transactionalOperator.transactional(persisted)
                            .flatMap(saved -> {
                                saved.markNotNew();
                                return bankClient.initiatePayment(new BankPaymentRequest(
                                        saved.getId(),
                                        saved.getOrderId(),
                                        saved.getAmount(),
                                        saved.getCurrency(),
                                        callbackUrl
                                ))
                                        .flatMap(bankResponse -> {
                                            saved.setStatus(PaymentStatus.PROCESSING);
                                            saved.setProviderSessionId(bankResponse.sessionId());
                                            saved.setUpdatedAt(Instant.now());
                                            return repository.save(saved)
                                                    .thenReturn(new PaymentSessionResponse(
                                                            saved.getId(),
                                                            saved.getStatus(),
                                                            bankResponse.redirectUrl()
                                                    ));
                                        });
                            });
                });
    }

    public Mono<PaymentResponse> handleCallback(BankPaymentCallback callback) {
        PaymentStatus status = "SUCCESS".equalsIgnoreCase(callback.status()) ? PaymentStatus.PAID : PaymentStatus.FAILED;
        OrderStatus orderStatus = status == PaymentStatus.PAID ? OrderStatus.PAID : OrderStatus.FAILED;

        Mono<Payment> updated = repository.findById(callback.paymentId())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found")))
                .flatMap(payment -> {
                    if (isFinalStatus(payment.getStatus()) && payment.getStatus() == status) {
                        return Mono.just(payment);
                    }
                    payment.setStatus(status);
                    payment.setUpdatedAt(Instant.now());
                    return repository.save(payment)
                            .flatMap(saved -> outboxService.enqueuePaymentResult(saved, status, callback.reason())
                                    .thenReturn(saved));
                });

        return transactionalOperator.transactional(updated)
                .flatMap(saved -> orderClient.updateStatus(saved.getOrderId(), orderStatus, callback.reason())
                        .thenReturn(saved))
                .map(this::toResponse)
                .doOnNext(response -> log.info("Payment callback handled: paymentId={}, orderId={}, status={}, reason={}",
                        response.id(), response.orderId(), response.status(), callback.reason()));
    }

    public Mono<PaymentResponse> getPayment(UUID paymentId) {
        return repository.findById(paymentId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found")))
                .map(this::toResponse);
    }

    public Flux<PaymentResponse> listPayments(UUID orderId, PaymentStatus status) {
        Criteria criteria = Criteria.empty();
        if (orderId != null) {
            criteria = criteria.and("orderId").is(orderId);
        }
        if (status != null) {
            criteria = criteria.and("status").is(status);
        }
        Query query = Query.query(criteria).sort(Sort.by(Sort.Order.desc("createdAt")));
        return template.select(query, Payment.class)
                .map(this::toResponse);
    }

    private boolean isFinalStatus(PaymentStatus status) {
        return status == PaymentStatus.PAID || status == PaymentStatus.FAILED;
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getProvider(),
                payment.getProviderSessionId(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
