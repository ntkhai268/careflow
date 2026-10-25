package com.careflow.emr.config;

import com.careflow.common.exception.GlobalExceptionHandler;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
@Import(GlobalExceptionHandler.class)
public class EmrSecurityConfiguration {
    @Bean
    public RequestInterceptor authenticatedFeignRequests() {
        return template -> {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                for (String header : new String[]{"Authorization", "X-User-Id", "X-User-Role", "X-Correlation-Id"}) {
                    String value = attributes.getRequest().getHeader(header);
                    if (value != null && !value.isBlank()) template.header(header, value);
                }
            }
        };
    }
}
