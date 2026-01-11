package com.example.reactive.product.service;

import com.example.reactive.product.model.Product;
import com.example.reactive.product.model.ProductEvent;
import com.example.reactive.product.model.ProductRequest;
import com.example.reactive.product.model.ProductResponse;
import com.example.reactive.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    private final ProductRepository repository;
    private final R2dbcEntityTemplate template;
    private final Sinks.Many<ProductEvent> productSink = Sinks.many().multicast().onBackpressureBuffer();

    public Flux<ProductResponse> search(String query, String category, BigDecimal minPrice, BigDecimal maxPrice,
                                        int page, int size) {
        Criteria criteria = Criteria.empty();
        if (query != null && !query.isBlank()) {
            criteria = criteria.and("name").like("%" + query.trim() + "%");
        }
        if (category != null && !category.isBlank()) {
            criteria = criteria.and("category").is(category.trim());
        }
        if (minPrice != null) {
            criteria = criteria.and("price").greaterThanOrEquals(minPrice);
        }
        if (maxPrice != null) {
            criteria = criteria.and("price").lessThanOrEquals(maxPrice);
        }

        Query filterQuery = Query.query(criteria)
                .limit(size)
                .offset((long) page * size)
                .sort(Sort.by(Sort.Order.desc("updatedAt")));

        return template.select(filterQuery, Product.class)
                .map(this::toResponse);
    }

    public Mono<ProductResponse> getById(UUID id) {
        return repository.findById(id)
                .map(this::toResponse)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found")));
    }

    public Mono<ProductResponse> create(ProductRequest request) {
        Product product = new Product(
                UUID.randomUUID(),
                request.name(),
                request.description(),
                request.category(),
                request.price(),
                request.currency(),
                request.stock(),
                request.imageUrl(),
                Instant.now()
        );

        return repository.save(product)
                .map(this::toResponse)
                .doOnNext(response -> {
                    log.info("Product created: id={}, name={}, category={}, price={}, stock={}",
                            response.id(), response.name(), response.category(), response.price(), response.stock());
                    publishEvent("PRODUCT_CREATED", response);
                });
    }

    public Mono<ProductResponse> update(UUID id, ProductRequest request) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found")))
                .flatMap(existing -> {
                    existing.setName(request.name());
                    existing.setDescription(request.description());
                    existing.setCategory(request.category());
                    existing.setPrice(request.price());
                    existing.setCurrency(request.currency());
                    existing.setStock(request.stock());
                    existing.setImageUrl(request.imageUrl());
                    existing.setUpdatedAt(Instant.now());
                    return repository.save(existing);
                })
                .map(this::toResponse)
                .doOnNext(response -> {
                    log.info("Product updated: id={}, name={}, category={}, price={}, stock={}",
                            response.id(), response.name(), response.category(), response.price(), response.stock());
                    publishEvent("PRODUCT_UPDATED", response);
                });
    }

    public Mono<Void> delete(UUID id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found")))
                .flatMap(existing -> repository.deleteById(existing.getId())
                        .thenReturn(existing))
                .doOnNext(existing -> {
                    ProductResponse response = toResponse(existing);
                    log.info("Product deleted: id={}, name={}, category={}",
                            response.id(), response.name(), response.category());
                    publishEvent("PRODUCT_DELETED", response);
                })
                .then();
    }

    public Flux<ServerSentEvent<ProductEvent>> stream() {
        ServerSentEvent<ProductEvent> connected = ServerSentEvent.<ProductEvent>builder()
                .comment("connected")
                .build();
        return Flux.concat(Mono.just(connected), productSink.asFlux()
                .map(event -> ServerSentEvent.builder(event)
                        .event(event.type())
                        .id(event.product().id().toString())
                        .build()));
    }

    private void publishEvent(String type, ProductResponse response) {
        productSink.tryEmitNext(new ProductEvent(type, response, Instant.now()));
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getCategory(),
                product.getPrice(),
                product.getCurrency(),
                product.getStock(),
                product.getImageUrl(),
                product.getUpdatedAt()
        );
    }
}
