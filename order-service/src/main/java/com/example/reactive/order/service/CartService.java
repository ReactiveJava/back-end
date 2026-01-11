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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CartService {
    private static final String CART_KEY_PREFIX = "cart:";

    private final ReactiveRedisTemplate<String, Cart> redisTemplate;
    private final ProductClient productClient;
    private final CartStreamService cartStreamService;
    private final ConcurrentMap<UUID, CachedProduct> productCache = new ConcurrentHashMap<>();
    private final long productCacheTtlMs;
    private final int productCacheMaxEntries;

    public CartService(ReactiveRedisTemplate<String, Cart> redisTemplate,
                       ProductClient productClient,
                       CartStreamService cartStreamService,
                       @Value("${app.products.cache-ttl-ms:30000}") long productCacheTtlMs,
                       @Value("${app.products.cache-max-entries:5000}") int productCacheMaxEntries) {
        this.redisTemplate = redisTemplate;
        this.productClient = productClient;
        this.cartStreamService = cartStreamService;
        this.productCacheTtlMs = productCacheTtlMs;
        this.productCacheMaxEntries = productCacheMaxEntries;
    }

    public Mono<CartResponse> getCart(String userId) {
        return redisTemplate.opsForValue()
                .get(cartKey(userId))
                .defaultIfEmpty(emptyCart(userId))
                .map(this::toResponse);
    }

    public Mono<CartResponse> addItem(String userId, UUID productId, int quantity) {
        return redisTemplate.opsForValue()
                .get(cartKey(userId))
                .defaultIfEmpty(emptyCart(userId))
                .flatMap(cart -> {
                    CartItem existing = cart.getItems().stream()
                            .filter(item -> item.getProductId().equals(productId))
                            .findFirst()
                            .orElse(null);
                    if (existing != null) {
                        List<CartItem> items = new ArrayList<>(cart.getItems());
                        items.forEach(item -> {
                            if (item.getProductId().equals(productId)) {
                                item.setQuantity(item.getQuantity() + quantity);
                            }
                        });
                        cart.setItems(items);
                        return Mono.just(refreshTotals(cart));
                    }
                    return getProductSummary(productId)
                            .map(product -> updateCartWithProduct(cart, product, quantity));
                })
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
                .flatMap(cart -> {
                    CartUpdate update = updateCartItem(cart, productId, quantity);
                    if (!update.changed()) {
                        return Mono.just(toResponse(update.cart()));
                    }
                    return saveCart(userId, update.cart())
                            .map(this::toResponse)
                            .doOnNext(response -> {
                                log.info("Cart item updated: userId={}, productId={}, quantity={}, total={}, items={}",
                                        userId, productId, quantity, response.total(), response.items().size());
                                cartStreamService.emit(response);
                            });
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

    private Mono<ProductSummary> getProductSummary(UUID productId) {
        CachedProduct cached = productCache.get(productId);
        if (cached != null && !cached.isExpired()) {
            return Mono.just(cached.product());
        }
        return productClient.getProduct(productId)
                .doOnNext(this::cacheProductSummary);
    }

    private void cacheProductSummary(ProductSummary product) {
        if (product == null || product.id() == null) {
            return;
        }
        if (productCache.size() >= productCacheMaxEntries) {
            productCache.clear();
        }
        productCache.put(product.id(), new CachedProduct(product, Instant.now().plusMillis(productCacheTtlMs)));
    }

    private CartUpdate updateCartItem(Cart cart, UUID productId, int quantity) {
        CartItem existing = cart.getItems().stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst()
                .orElse(null);
        if (existing == null) {
            return new CartUpdate(cart, false);
        }
        if (quantity == existing.getQuantity()) {
            return new CartUpdate(cart, false);
        }
        List<CartItem> items = new ArrayList<>(cart.getItems());
        if (quantity == 0) {
            items.removeIf(item -> item.getProductId().equals(productId));
        } else {
            items.forEach(item -> {
                if (item.getProductId().equals(productId)) {
                    item.setQuantity(quantity);
                }
            });
        }
        cart.setItems(items);
        return new CartUpdate(refreshTotals(cart), true);
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

    private record CachedProduct(ProductSummary product, Instant expiresAt) {
        boolean isExpired() {
            return expiresAt != null && expiresAt.isBefore(Instant.now());
        }
    }

    private record CartUpdate(Cart cart, boolean changed) {}
}
