package com.example.reactive.payment.service;

import com.example.reactive.payment.client.AdminClient;
import com.example.reactive.payment.client.BankClient;
import com.example.reactive.payment.client.NotificationClient;
import com.example.reactive.payment.client.OrderClient;
import com.example.reactive.payment.model.AdminEvent;
import com.example.reactive.payment.model.BankPaymentCallback;
import com.example.reactive.payment.model.BankPaymentRequest;
import com.example.reactive.payment.model.NotificationEvent;
import com.example.reactive.payment.model.OrderSnapshot;
import com.example.reactive.payment.model.OrderStatus;
import com.example.reactive.payment.model.Payment;
import com.example.reactive.payment.model.PaymentRequest;
import com.example.reactive.payment.model.PaymentResponse;
import com.example.reactive.payment.model.PaymentSessionResponse;
import com.example.reactive.payment.model.PaymentStatus;
import com.example.reactive.payment.repository.PaymentRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository repository;
    private final R2dbcEntityTemplate template;
    private final OrderClient orderClient;
    private final BankClient bankClient;
    private final NotificationClient notificationClient;
    private final AdminClient adminClient;
    private final String callbackUrl;

    public PaymentService(PaymentRepository repository,
                          R2dbcEntityTemplate template,
                          OrderClient orderClient,
                          BankClient bankClient,
                          NotificationClient notificationClient,
                          AdminClient adminClient,
                          @Value("${app.bank.callback-url}") String callbackUrl) {
        this.repository = repository;
        this.template = template;
        this.orderClient = orderClient;
        this.bankClient = bankClient;
        this.notificationClient = notificationClient;
        this.adminClient = adminClient;
        this.callbackUrl = callbackUrl;
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
                    return repository.save(payment)
                            .doOnNext(saved -> log.info(
                                    "Payment initiated: paymentId={}, orderId={}, userId={}, amount={}, currency={}, method={}",
                                    saved.getId(), saved.getOrderId(), saved.getUserId(),
                                    saved.getAmount(), saved.getCurrency(), saved.getProvider()))
                            .flatMap(saved -> adminClient.publish(new AdminEvent(
                                    "PAYMENT_INITIATED",
                                    saved.getOrderId(),
                                    Instant.now()
                            )).thenReturn(saved))
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

        return repository.findById(callback.paymentId())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found")))
                .flatMap(payment -> {
                    payment.setStatus(status);
                    payment.setUpdatedAt(Instant.now());
                    return repository.save(payment);
                })
                .flatMap(saved -> orderClient.updateStatus(saved.getOrderId(), orderStatus, callback.reason())
                        .then(sendPaymentEvents(saved, status, callback.reason()))
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

    private Mono<Void> sendPaymentEvents(Payment payment, PaymentStatus status, String reason) {
        String message = status == PaymentStatus.PAID ? "Payment successful" : "Payment failed";
        NotificationEvent event = new NotificationEvent(
                payment.getUserId(),
                "PAYMENT_" + status.name(),
                reason == null ? message : reason,
                Map.of("paymentId", payment.getId(), "orderId", payment.getOrderId(), "status", status),
                Instant.now()
        );
        String adminType = status == PaymentStatus.PAID ? "PAYMENT_SUCCESS" : "PAYMENT_FAILED";
        AdminEvent adminEvent = new AdminEvent(adminType, payment.getOrderId(), Instant.now());
        return notificationClient.publish(event).then(adminClient.publish(adminEvent));
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
