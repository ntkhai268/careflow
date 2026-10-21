package com.careflow.patient.config;

import com.careflow.common.exception.BusinessException;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class PatientSecurityConfiguration {
    @Bean
    public RequestInterceptor authenticatedFeignRequests() {
        return template -> {
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
                throw new BusinessException(401, "Missing authenticated request context");
            }
            String authorization = attributes.getRequest().getHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() == 7) {
                throw new BusinessException(401, "Missing Bearer token");
            }
            for (String header : new String[]{"Authorization", "X-User-Id", "X-User-Role", "X-Correlation-Id"}) {
                String value = attributes.getRequest().getHeader(header);
                if (value != null && !value.isBlank()) {
                    template.removeHeader(header);
                    template.header(header, value);
                }
            }
        };
    }
}
