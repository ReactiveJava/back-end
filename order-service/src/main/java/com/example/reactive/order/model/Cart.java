package com.example.reactive.order.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Cart {
    private String userId;
    private List<CartItem> items = new ArrayList<>();
    private BigDecimal total;
    private String currency;
    private Instant updatedAt;
}
