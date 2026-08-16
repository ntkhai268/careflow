package com.careflow.directory.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CareFlow Hospital Directory Service API")
                        .version("1.0.0")
                        .description("API Documentation for CareFlow Master Data Directory Service"));
    }
}
