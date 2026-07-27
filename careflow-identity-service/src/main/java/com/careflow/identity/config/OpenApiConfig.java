package com.careflow.identity.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI identityOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CareFlow Identity & eKYC API")
                        .version("1.0.0")
                        .description("API đăng ký, đăng nhập, quản lý phiên và eKYC giả lập của CareFlow")
                        .license(new License().name("Internal educational project")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token nhận từ POST /api/auth/login")));
    }
}
