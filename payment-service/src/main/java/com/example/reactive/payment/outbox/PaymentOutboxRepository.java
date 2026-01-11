package com.example.reactive.payment.outbox;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class PaymentOutboxRepository {
    private final DatabaseClient databaseClient;

    public Mono<Void> insertIgnoreDuplicate(PaymentOutboxEvent event) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                        INSERT INTO payment_outbox (
                            id,
                            payment_id,
                            order_id,
                            target,
                            event_type,
                            payload,
                            status,
                            attempts,
                            next_attempt_at,
                            last_attempt_at,
                            sent_at,
                            created_at
                        ) VALUES (
                            :id,
                            :paymentId,
                            :orderId,
                            :target,
                            :eventType,
                            :payload,
                            :status,
                            :attempts,
                            :nextAttemptAt,
                            :lastAttemptAt,
                            :sentAt,
                            :createdAt
                        )
                        ON CONFLICT (payment_id, event_type, target) DO NOTHING
                        """)
                .bind("id", event.getId())
                .bind("paymentId", event.getPaymentId())
                .bind("orderId", event.getOrderId())
                .bind("target", event.getTarget().name())
                .bind("eventType", event.getEventType())
                .bind("payload", event.getPayload())
                .bind("status", event.getStatus().name())
                .bind("attempts", event.getAttempts())
                .bind("createdAt", event.getCreatedAt());
        spec = bindNullable(spec, "nextAttemptAt", event.getNextAttemptAt(), Instant.class);
        spec = bindNullable(spec, "lastAttemptAt", event.getLastAttemptAt(), Instant.class);
        spec = bindNullable(spec, "sentAt", event.getSentAt(), Instant.class);
        return spec.fetch().rowsUpdated().then();
    }

    public Flux<PaymentOutboxEvent> findReadyEvents(int limit, int maxAttempts, Instant now) {
        return databaseClient.sql("""
                        SELECT id,
                               payment_id,
                               order_id,
                               target,
                               event_type,
                               payload,
                               status,
                               attempts,
                               next_attempt_at,
                               last_attempt_at,
                               sent_at,
                               created_at
                        FROM payment_outbox
                        WHERE status IN ('PENDING', 'FAILED')
                          AND attempts < :maxAttempts
                          AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
                        ORDER BY created_at
                        LIMIT :limit
                        """)
                .bind("now", now)
                .bind("maxAttempts", maxAttempts)
                .bind("limit", limit)
                .map((row, metadata) -> {
                    PaymentOutboxEvent event = new PaymentOutboxEvent();
                    event.setId(row.get("id", UUID.class));
                    event.setPaymentId(row.get("payment_id", UUID.class));
                    event.setOrderId(row.get("order_id", UUID.class));
                    event.setTarget(OutboxTarget.valueOf(row.get("target", String.class)));
                    event.setEventType(row.get("event_type", String.class));
                    event.setPayload(row.get("payload", String.class));
                    event.setStatus(OutboxStatus.valueOf(row.get("status", String.class)));
                    Integer attempts = row.get("attempts", Integer.class);
                    event.setAttempts(attempts == null ? 0 : attempts);
                    event.setNextAttemptAt(row.get("next_attempt_at", Instant.class));
                    event.setLastAttemptAt(row.get("last_attempt_at", Instant.class));
                    event.setSentAt(row.get("sent_at", Instant.class));
                    event.setCreatedAt(row.get("created_at", Instant.class));
                    return event;
                })
                .all();
    }

    public Mono<Boolean> claimEvent(UUID id, Instant now, Instant nextAttemptAt) {
        return databaseClient.sql("""
                        UPDATE payment_outbox
                        SET status = 'PROCESSING',
                            attempts = attempts + 1,
                            last_attempt_at = :now,
                            next_attempt_at = :nextAttemptAt
                        WHERE id = :id
                          AND status IN ('PENDING', 'FAILED')
                        """)
                .bind("now", now)
                .bind("nextAttemptAt", nextAttemptAt)
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .map(rows -> rows > 0);
    }

    public Mono<Void> markSent(UUID id, Instant sentAt) {
        return databaseClient.sql("""
                        UPDATE payment_outbox
                        SET status = 'SENT',
                            sent_at = :sentAt
                        WHERE id = :id
                        """)
                .bind("sentAt", sentAt)
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .then();
    }

    public Mono<Void> markFailed(UUID id, Instant nextAttemptAt) {
        return databaseClient.sql("""
                        UPDATE payment_outbox
                        SET status = 'FAILED',
                            next_attempt_at = :nextAttemptAt
                        WHERE id = :id
                        """)
                .bind("nextAttemptAt", nextAttemptAt)
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .then();
    }

    public Mono<Void> deleteAll() {
        return databaseClient.sql("DELETE FROM payment_outbox")
                .fetch()
                .rowsUpdated()
                .then();
    }

    private DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec,
                                                           String name,
                                                           Instant value,
                                                           Class<?> type) {
        if (value == null) {
            return spec.bindNull(name, type);
        }
        return spec.bind(name, value);
    }
}
