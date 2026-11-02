package com.careflow.emr.config;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.exception.GlobalExceptionHandler;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Configuration
@Import(GlobalExceptionHandler.class)
public class EmrSecurityConfiguration {
    private static final List<String> FORWARDED_HEADERS = List.of(
            "Authorization",
            AppConstants.HEADER_USER_ID,
            AppConstants.HEADER_USER_ROLE,
            AppConstants.HEADER_CORRELATION_ID
    );

    @Bean
    public RequestInterceptor authenticatedFeignRequests() {
        return template -> {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                for (String header : FORWARDED_HEADERS) {
                    String value = attributes.getRequest().getHeader(header);
                    if (value != null && !value.isBlank()) {
                        template.header(header, value);
                    }
                }
            }
        };
    }
}
