package com.careflow.consultation.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignClientInterceptorConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return (RequestTemplate template) -> {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                
                String userId = request.getHeader("X-User-Id");
                if (userId != null && !userId.isBlank()) {
                    template.header("X-User-Id", userId);
                } else {
                    // Fallback system user ID if call originated without X-User-Id header
                    template.header("X-User-Id", "d0000001-0000-0000-0000-000000000001");
                }

                String userRole = request.getHeader("X-User-Role");
                if (userRole != null && !userRole.isBlank()) {
                    template.header("X-User-Role", userRole);
                } else {
                    template.header("X-User-Role", "DOCTOR");
                }

                String correlationId = request.getHeader("X-Correlation-Id");
                if (correlationId != null && !correlationId.isBlank()) {
                    template.header("X-Correlation-Id", correlationId);
                }
            } else {
                // Default headers for internal/background Feign calls
                template.header("X-User-Id", "d0000001-0000-0000-0000-000000000001");
                template.header("X-User-Role", "DOCTOR");
            }
        };
    }
}
