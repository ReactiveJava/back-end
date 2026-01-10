package com.example.reactive.admin.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Admin Service API",
                version = "v1",
                description = "Reactive admin metrics and aggregation"
        )
)
public class OpenApiConfig {
}
