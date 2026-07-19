package com.careflow.patient.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI careflowPatientOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CareFlow Patient Service API")
                        .description("API quản lý hồ sơ bệnh nhân — CareFlow Healthcare Platform")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("CareFlow Team")
                                .email("team@careflow.vn")));
    }
}
