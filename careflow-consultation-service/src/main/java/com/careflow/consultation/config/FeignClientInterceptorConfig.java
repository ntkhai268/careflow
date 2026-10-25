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
                }

                String userRole = request.getHeader("X-User-Role");
                if (userRole != null && !userRole.isBlank()) {
                    template.header("X-User-Role", userRole);
                }

                String authorization = request.getHeader("Authorization");
                if (authorization != null && !authorization.isBlank()) {
                    template.header("Authorization", authorization);
                }

                String correlationId = request.getHeader("X-Correlation-Id");
                if (correlationId != null && !correlationId.isBlank()) {
                    template.header("X-Correlation-Id", correlationId);
                }
            }
        };
    }
}
