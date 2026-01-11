package com.example.reactive.order.model;

import java.math.BigDecimal;
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

@Table("order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem implements Persistable<UUID> {
    @Id
    private UUID id;
    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean newEntity;
    @Column("order_id")
    private UUID orderId;
    @Column("product_id")
    private UUID productId;
    private String name;
    private BigDecimal price;
    private int quantity;

    public OrderItem(UUID id, UUID orderId, UUID productId, String name, BigDecimal price, int quantity) {
        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.newEntity = true;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }
}
