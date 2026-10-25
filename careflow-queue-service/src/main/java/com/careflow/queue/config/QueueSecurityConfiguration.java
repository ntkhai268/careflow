package com.careflow.queue.config;

import com.careflow.common.exception.BusinessException;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class QueueSecurityConfiguration {
    @Bean
    public RequestInterceptor authenticatedFeignRequests() {
        return template -> {
            // Feign interceptors run before the target's /api/directory prefix is applied.
            boolean publicDirectory=template.feignTarget()!=null
                    && template.feignTarget().type()==com.careflow.queue.client.ReceptionRoomClient.class
                    && "GET".equals(template.method())
                    && (template.path().matches("(?:/api/directory)?/rooms")
                        || template.path().matches("(?:/api/directory)?/departments/[A-Za-z0-9_-]+"));
            if(publicDirectory)return;
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
                throw new BusinessException(401, "Missing authenticated request context");
            }
            String authorization = attributes.getRequest().getHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() == 7) {
                throw new BusinessException(401, "Missing Bearer token");
            }
            for (String header : new String[]{"Authorization", "X-User-Id", "X-User-Role", "X-Correlation-Id"}) {
                template.removeHeader(header);
                String value = attributes.getRequest().getHeader(header);
                if (value != null && !value.isBlank()) template.header(header, value);
            }
        };
    }
}
