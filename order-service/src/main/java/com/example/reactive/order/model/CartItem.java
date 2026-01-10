package com.example.reactive.order.model;

import java.math.BigDecimal;
import java.util.UUID;

public class CartItem {
    private UUID productId;
    private String name;
    private BigDecimal price;
    private String currency;
    private int quantity;
    private String imageUrl;

    public CartItem() {
    }

    public CartItem(UUID productId, String name, BigDecimal price, String currency, int quantity, String imageUrl) {
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.quantity = quantity;
        this.imageUrl = imageUrl;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
