package com.careflow.consultation.config;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class FeignIdentityPropagationTest {
    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void missingRequestDoesNotFabricateDoctorIdentity() {
        RequestTemplate template = new RequestTemplate();
        new FeignClientInterceptorConfig().requestInterceptor().apply(template);
        assertThat(template.headers()).doesNotContainKeys("X-User-Id", "X-User-Role", "Authorization");
    }

    @Test
    void missingHeadersDoNotFabricateDoctorIdentity() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        RequestTemplate template = new RequestTemplate();
        new FeignClientInterceptorConfig().requestInterceptor().apply(template);
        assertThat(template.headers()).doesNotContainKeys("X-User-Id", "X-User-Role", "Authorization");
    }

    @Test
    void bearerAndCorrelationAreForwardedWithCurrentIdentity() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer test-token");
        request.addHeader("X-User-Id", "d0000001-0000-0000-0000-000000000001");
        request.addHeader("X-User-Role", "DOCTOR");
        request.addHeader("X-Correlation-Id", "12345678-1234-1234-1234-123456789012");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestTemplate template = new RequestTemplate();
        new FeignClientInterceptorConfig().requestInterceptor().apply(template);
        assertThat(template.headers().get("Authorization")).containsExactly("Bearer test-token");
        assertThat(template.headers().get("X-User-Id")).containsExactly("d0000001-0000-0000-0000-000000000001");
        assertThat(template.headers().get("X-Correlation-Id")).containsExactly("12345678-1234-1234-1234-123456789012");
    }
}
