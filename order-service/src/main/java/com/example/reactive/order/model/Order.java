package com.example.reactive.order.model;

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

@Table("orders")
@Getter
@Setter
@NoArgsConstructor
public class Order implements Persistable<UUID> {
    @Id
    private UUID id;
    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean newEntity;
    @Column("user_id")
    private String userId;
    private OrderStatus status;
    private BigDecimal total;
    private String currency;
    @Column("payment_method")
    private String paymentMethod;
    @Column("shipping_name")
    private String shippingName;
    @Column("shipping_phone")
    private String shippingPhone;
    @Column("shipping_address")
    private String shippingAddress;
    @Column("shipping_city")
    private String shippingCity;
    @Column("shipping_postal_code")
    private String shippingPostalCode;
    @Column("created_at")
    private Instant createdAt;
    @Column("updated_at")
    private Instant updatedAt;

    public Order(UUID id, String userId, OrderStatus status, BigDecimal total, String currency,
                 String paymentMethod, String shippingName, String shippingPhone, String shippingAddress,
                 String shippingCity, String shippingPostalCode, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.status = status;
        this.total = total;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.shippingName = shippingName;
        this.shippingPhone = shippingPhone;
        this.shippingAddress = shippingAddress;
        this.shippingCity = shippingCity;
        this.shippingPostalCode = shippingPostalCode;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.newEntity = true;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }
}
