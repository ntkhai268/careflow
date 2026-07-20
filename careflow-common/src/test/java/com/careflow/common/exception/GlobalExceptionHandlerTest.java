package com.careflow.common.exception;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.web.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void generalErrorDoesNotLeakImplementationDetails() {
        var request = request("/api/private");

        var response = handler.handleGeneral(
                new RuntimeException("jdbc password=secret"),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Internal server error");
        assertThat(response.getBody().getMessage()).doesNotContain("secret", "jdbc");
        assertThat(response.getBody().getData().correlationId()).isEqualTo("test-correlation");
    }

    @Test
    void businessErrorPreservesSafeCodeAndStatus() {
        var response = handler.handleBusinessException(
                new BusinessException(409, "USERNAME_EXISTS", "Username already exists"),
                request("/api/users"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getData().code()).isEqualTo("USERNAME_EXISTS");
    }

    private MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest("POST", path);
        request.setAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE, "test-correlation");
        request.addHeader(AppConstants.HEADER_CORRELATION_ID, "test-correlation");
        return request;
    }
}
