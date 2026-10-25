package com.careflow.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtSessionFilterFailureTest {
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 503})
    void failedValidationCannotReachControllerOrLeaveAnAuthenticatedContext(int status) throws Exception {
        JwtService jwt = mock(JwtService.class);
        RuntimeException failure = status == 401 ? new JwtException("Revoked session")
                : new DataAccessResourceFailureException("Synthetic authority outage");
        when(jwt.parse("fixture-token")).thenThrow(failure);
        var filter = new JwtAuthenticationFilter(jwt, new ObjectMapper().findAndRegisterModules(), mock(DpopProofService.class), mock(CallerRegistry.class));
        var request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/session");
        request.setMethod("GET");
        request.addHeader("Authorization", "Bearer fixture-token");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { throw new AssertionError("Must not reach controller"); });
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
