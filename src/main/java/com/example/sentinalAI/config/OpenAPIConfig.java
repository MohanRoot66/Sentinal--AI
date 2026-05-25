package com.example.sentinalAI.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sentinel AI - POC API")
                        .version("1.0.0")
                        .description("AI-powered silent failure detection and operational intelligence platform")
                        .contact(new Contact()
                                .name("Sentinel AI Team")
                                .email("support@sentinelai.com")));
    }
}

