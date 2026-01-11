package com.example.reactive.order.model;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {
    private UUID productId;
    private String name;
    private BigDecimal price;
    private String currency;
    private int quantity;
    private String imageUrl;
}
