package com.example.reactive.order.service;

import com.example.reactive.order.client.ProductClient;
import com.example.reactive.order.model.Cart;
import com.example.reactive.order.model.CartItem;
import com.example.reactive.order.model.CartResponse;
import com.example.reactive.order.model.ProductSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class CartService {
    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private static final String CART_KEY_PREFIX = "cart:";

    private final ReactiveRedisTemplate<String, Cart> redisTemplate;
    private final ProductClient productClient;
    private final CartStreamService cartStreamService;

    public CartService(ReactiveRedisTemplate<String, Cart> redisTemplate,
                       ProductClient productClient,
                       CartStreamService cartStreamService) {
        this.redisTemplate = redisTemplate;
        this.productClient = productClient;
        this.cartStreamService = cartStreamService;
    }

    public Mono<CartResponse> getCart(String userId) {
        return redisTemplate.opsForValue()
                .get(cartKey(userId))
                .defaultIfEmpty(emptyCart(userId))
                .map(this::toResponse);
    }

    public Mono<CartResponse> addItem(String userId, UUID productId, int quantity) {
        Mono<Cart> cartMono = redisTemplate.opsForValue()
                .get(cartKey(userId))
                .defaultIfEmpty(emptyCart(userId));

        Mono<ProductSummary> productMono = productClient.getProduct(productId);

        return Mono.zip(cartMono, productMono)
                .map(tuple -> updateCartWithProduct(tuple.getT1(), tuple.getT2(), quantity))
                .flatMap(cart -> saveCart(userId, cart))
                .map(this::toResponse)
                .doOnNext(response -> {
                    log.info("Cart item added: userId={}, productId={}, quantity={}, total={}, items={}",
                            userId, productId, quantity, response.total(), response.items().size());
                    cartStreamService.emit(response);
                });
    }

    public Mono<CartResponse> updateItem(String userId, UUID productId, int quantity) {
        return redisTemplate.opsForValue()
                .get(cartKey(userId))
                .defaultIfEmpty(emptyCart(userId))
                .map(cart -> {
                    List<CartItem> items = new ArrayList<>(cart.getItems());
                    items.removeIf(item -> item.getProductId().equals(productId) && quantity == 0);
                    items.forEach(item -> {
                        if (item.getProductId().equals(productId)) {
                            item.setQuantity(quantity);
                        }
                    });
                    cart.setItems(items);
                    return refreshTotals(cart);
                })
                .flatMap(cart -> saveCart(userId, cart))
                .map(this::toResponse)
                .doOnNext(response -> {
                    log.info("Cart item updated: userId={}, productId={}, quantity={}, total={}, items={}",
                            userId, productId, quantity, response.total(), response.items().size());
                    cartStreamService.emit(response);
                });
    }

    public Mono<CartResponse> removeItem(String userId, UUID productId) {
        return redisTemplate.opsForValue()
                .get(cartKey(userId))
                .defaultIfEmpty(emptyCart(userId))
                .map(cart -> {
                    List<CartItem> items = new ArrayList<>(cart.getItems());
                    items.removeIf(item -> item.getProductId().equals(productId));
                    cart.setItems(items);
                    return refreshTotals(cart);
                })
                .flatMap(cart -> saveCart(userId, cart))
                .map(this::toResponse)
                .doOnNext(response -> {
                    log.info("Cart item removed: userId={}, productId={}, total={}, items={}",
                            userId, productId, response.total(), response.items().size());
                    cartStreamService.emit(response);
                });
    }

    public Mono<Void> clearCart(String userId) {
        return redisTemplate.opsForValue()
                .delete(cartKey(userId))
                .doOnSuccess(ignored -> {
                    log.info("Cart cleared: userId={}", userId);
                    cartStreamService.emit(toResponse(emptyCart(userId)));
                })
                .then();
    }

    private Cart updateCartWithProduct(Cart cart, ProductSummary product, int quantity) {
        List<CartItem> items = new ArrayList<>(cart.getItems());
        CartItem existing = items.stream()
                .filter(item -> item.getProductId().equals(product.id()))
                .findFirst()
                .orElse(null);

        if (existing == null) {
            items.add(new CartItem(product.id(), product.name(), product.price(), product.currency(), quantity,
                    product.imageUrl()));
        } else {
            existing.setQuantity(existing.getQuantity() + quantity);
        }
        cart.setItems(items);
        cart.setCurrency(product.currency());
        return refreshTotals(cart);
    }

    private Cart refreshTotals(Cart cart) {
        BigDecimal total = cart.getItems().stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        cart.setTotal(total);
        cart.setUpdatedAt(Instant.now());
        return cart;
    }

    private Mono<Cart> saveCart(String userId, Cart cart) {
        return redisTemplate.opsForValue()
                .set(cartKey(userId), cart)
                .thenReturn(cart);
    }

    private Cart emptyCart(String userId) {
        return new Cart(userId, new ArrayList<>(), BigDecimal.ZERO, "USD", Instant.now());
    }

    private CartResponse toResponse(Cart cart) {
        return new CartResponse(cart.getUserId(), cart.getItems(), cart.getTotal(), cart.getCurrency(), cart.getUpdatedAt());
    }

    private String cartKey(String userId) {
        return CART_KEY_PREFIX + userId;
    }
}
