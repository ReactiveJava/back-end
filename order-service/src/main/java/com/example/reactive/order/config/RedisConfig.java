package com.example.reactive.order.config;

import com.example.reactive.order.model.Cart;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean
    public ReactiveRedisTemplate<String, Cart> cartRedisTemplate(
            ReactiveRedisConnectionFactory factory,
            ObjectMapper objectMapper
    ) {
        Jackson2JsonRedisSerializer<Cart> serializer = new Jackson2JsonRedisSerializer<>(Cart.class);
        serializer.setObjectMapper(objectMapper);

        RedisSerializationContext<String, Cart> context = RedisSerializationContext
                .<String, Cart>newSerializationContext(new StringRedisSerializer())
                .value(SerializationPair.fromSerializer(serializer))
                .build();
        return new ReactiveRedisTemplate<>(factory, context);
    }
}
