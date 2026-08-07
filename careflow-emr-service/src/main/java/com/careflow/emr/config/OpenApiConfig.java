package com.careflow.emr.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CareFlow EMR Service API")
                        .version("1.0.0")
                        .description("Dịch vụ Hồ sơ Bệnh án Điện tử Liên thông (CareFlow EMR Service)")
                        .contact(new Contact()
                                .name("CareFlow Team")
                                .email("support@careflow.com")));
    }
}
