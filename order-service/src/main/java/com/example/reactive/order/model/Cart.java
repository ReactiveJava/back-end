package com.example.reactive.order.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Cart {
    private String userId;
    private List<CartItem> items = new ArrayList<>();
    private BigDecimal total;
    private String currency;
    private Instant updatedAt;

    public Cart() {
    }

    public Cart(String userId, List<CartItem> items, BigDecimal total, String currency, Instant updatedAt) {
        this.userId = userId;
        this.items = items;
        this.total = total;
        this.currency = currency;
        this.updatedAt = updatedAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<CartItem> getItems() {
        return items;
    }

    public void setItems(List<CartItem> items) {
        this.items = items;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
