package com.careflow.common.security;

import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActiveSessionFilterTest {
    @Test
    void allServletHeaderAccessorsUseVerifiedIdentity() throws Exception {
        ActiveSessionClient client = mock(ActiveSessionClient.class);
        UUID user = UUID.randomUUID();
        when(client.verifyRequest(any(), any(), any(), any(), any(), any(), any())).thenReturn(new ActiveSessionClient.Principal(true, user, "PATIENT", UUID.randomUUID(), Instant.now().plusSeconds(60)));
        var request = request("/api/patients/" + UUID.randomUUID());
        AtomicReference<HttpServletRequest> forwarded = new AtomicReference<>();
        filter(client).doFilter(request, new MockHttpServletResponse(), (req, res) -> forwarded.set((HttpServletRequest) req));
        var result = forwarded.get();
        assertThat(result.getHeader("X-User-Id")).isEqualTo(user.toString());
        assertThat(result.getHeader("x-user-role")).isEqualTo("PATIENT");
        assertThat(Collections.list(result.getHeaders("X-User-Role"))).containsExactly("PATIENT");
        assertThat(Collections.list(result.getHeaderNames()).stream().filter(x -> x.equalsIgnoreCase("X-User-Role"))).hasSize(1);
        assertThat(UUID.fromString(result.getHeader("X-Correlation-Id"))).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 503})
    void failedAuthorityCannotExecuteBusinessRequest(int status) throws Exception {
        ActiveSessionClient client = mock(ActiveSessionClient.class);
        when(client.verifyRequest(any(), any(), any(), any(), any(), any(), any())).thenThrow(new BusinessException(status, "Synthetic authority denial"));
        var response = new MockHttpServletResponse();
        filter(client).doFilter(request("/api/queues/entries"), response,
                (req, res) -> { throw new AssertionError("Business request must not execute"); });
        assertThat(response.getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/directory/departments", "/api/directory/departments/NOI", "/api/directory/rooms", "/api/directory/doctors"})
    void explicitPublicCatalogsStripForgedIdentity(String path) throws Exception {
        ActiveSessionClient client = mock(ActiveSessionClient.class);
        filter(client).doFilter(request(path), new MockHttpServletResponse(), (req, res) -> {
            var forwarded = (HttpServletRequest) req;
            assertThat(forwarded.getHeader("X-User-Id")).isNull();
            assertThat(forwarded.getHeader("X-User-Role")).isNull();
            assertThat(Collections.list(forwarded.getHeaders("X-User-Role"))).isEmpty();
        });
        verifyNoInteractions(client);
    }

    private MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest();
        request.setRequestURI(path);
        request.setMethod("GET");
        request.addHeader("Authorization", "Bearer fixture-token");
        request.addHeader("X-User-Id", UUID.randomUUID().toString());
        request.addHeader("X-User-Role", "ADMIN");
        return request;
    }

    private ActiveSessionFilter filter(ActiveSessionClient client) {
        return new ActiveSessionFilter(client, new ObjectMapper().findAndRegisterModules());
    }
}
