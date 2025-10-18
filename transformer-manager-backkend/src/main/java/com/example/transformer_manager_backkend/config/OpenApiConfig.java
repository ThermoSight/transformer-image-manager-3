package com.example.transformer_manager_backkend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components())
                .info(new Info().title("Transformer Manager Backend API").version("v1"));
    }
}

/*
 * After build and start the Spring Boot backend, the Swagger UI will be
 * available and show all our controllers/endpoints (GET/PUT/POST/DELETE).
 * Typical URLs:
 * Swagger UI: http://localhost:8080/swagger-ui/index.html
 * OpenAPI JSON: http://localhost:8080/v3/api-docs
 */