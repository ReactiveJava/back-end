package com.example.reactive.payment.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment implements Persistable<UUID> {
    @Id
    private UUID id;
    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean newEntity;
    @Column("order_id")
    private UUID orderId;
    @Column("user_id")
    private String userId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String provider;
    @Column("provider_session_id")
    private String providerSessionId;
    @Column("created_at")
    private Instant createdAt;
    @Column("updated_at")
    private Instant updatedAt;

    public Payment(UUID id, UUID orderId, String userId, BigDecimal amount, String currency,
                   PaymentStatus status, String provider, String providerSessionId,
                   Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.provider = provider;
        this.providerSessionId = providerSessionId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.newEntity = true;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public void markNotNew() {
        this.newEntity = false;
    }
}
