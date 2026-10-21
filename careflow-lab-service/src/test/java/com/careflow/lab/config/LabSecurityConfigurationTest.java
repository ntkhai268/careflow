package com.careflow.lab.config;

import com.careflow.common.exception.BusinessException;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabSecurityConfigurationTest {
    private final LabSecurityConfiguration configuration = new LabSecurityConfiguration();

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void replacesTemplateIdentityWithAuthenticatedRequestAndPropagatesBearer() {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer verified-token");
        request.addHeader("X-User-Id", "verified-user");
        request.addHeader("X-User-Role", "PATIENT");
        request.addHeader("X-Correlation-Id", "request-correlation");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var template = new RequestTemplate();
        template.header("X-User-Role", "ADMIN");
        template.header("Authorization", "Bearer stale-token");

        configuration.authenticatedFeignRequests().apply(template);

        assertThat(template.headers().get("Authorization")).containsExactly("Bearer verified-token");
        assertThat(template.headers().get("X-User-Id")).containsExactly("verified-user");
        assertThat(template.headers().get("X-User-Role")).containsExactly("PATIENT");
        assertThat(template.headers().get("X-Correlation-Id")).containsExactly("request-correlation");
    }

    @Test
    void rejectsBackgroundCallWithoutUserContext() {
        assertThatThrownBy(() -> configuration.authenticatedFeignRequests().apply(new RequestTemplate()))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getStatus()).isEqualTo(401));
    }

    @Test
    void rejectsHeaderOnlyContextWithoutBearer() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-User-Role", "DOCTOR");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThatThrownBy(() -> configuration.authenticatedFeignRequests().apply(new RequestTemplate()))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getStatus()).isEqualTo(401));
    }
}
