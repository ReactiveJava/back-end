package com.example.reactive.bankmock.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Bank Mock API",
                version = "v1",
                description = "Mock bank with latency and errors"
        )
)
public class OpenApiConfig {
}
