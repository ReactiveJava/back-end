package com.example.reactive.payment.outbox;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("payment_outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOutboxEvent {
    @Id
    private UUID id;
    @Column("payment_id")
    private UUID paymentId;
    @Column("order_id")
    private UUID orderId;
    private OutboxTarget target;
    @Column("event_type")
    private String eventType;
    private String payload;
    private OutboxStatus status;
    private int attempts;
    @Column("next_attempt_at")
    private Instant nextAttemptAt;
    @Column("last_attempt_at")
    private Instant lastAttemptAt;
    @Column("sent_at")
    private Instant sentAt;
    @Column("created_at")
    private Instant createdAt;
}
