package com.example.reactive.product.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("products")
@Getter
@Setter
@NoArgsConstructor
public class Product implements Persistable<UUID> {
    @Id
    private UUID id;
    @Transient
    private boolean newEntity;
    private String name;
    private String description;
    private String category;
    private BigDecimal price;
    private String currency;
    private Integer stock;
    @Column("image_url")
    private String imageUrl;
    @Column("updated_at")
    private Instant updatedAt;

    public Product(UUID id,
                   String name,
                   String description,
                   String category,
                   BigDecimal price,
                   String currency,
                   Integer stock,
                   String imageUrl,
                   Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.price = price;
        this.currency = currency;
        this.stock = stock;
        this.imageUrl = imageUrl;
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
