package com.careflow.common.web;

import com.careflow.common.constants.AppConstants;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void propagatesSafeCorrelationId() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.addHeader(AppConstants.HEADER_CORRELATION_ID, "request-123");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(AppConstants.HEADER_CORRELATION_ID)).isEqualTo("request-123");
        assertThat(request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE)).isEqualTo("request-123");
    }

    @Test
    void replacesUnsafeCorrelationId() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.addHeader(AppConstants.HEADER_CORRELATION_ID, "bad\r\nheader");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(AppConstants.HEADER_CORRELATION_ID))
                .matches("[0-9a-f-]{36}");
    }
}
