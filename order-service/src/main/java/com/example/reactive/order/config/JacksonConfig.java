package com.example.reactive.order.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {
    @Bean
    public Module blackbirdModule() {
        return new BlackbirdModule();
    }
}
