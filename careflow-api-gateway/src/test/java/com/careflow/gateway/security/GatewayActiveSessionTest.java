package com.careflow.gateway.security;

import com.careflow.common.exception.BusinessException;
import com.careflow.common.security.ActiveSessionClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GatewayActiveSessionTest {
    private static final String SECRET = "gateway_session_key_012345678901234567890123456789012345";

    @ParameterizedTest
    @ValueSource(ints = {401, 503})
    void authorityRejectionNeverReachesRoutedService(int status) {
        var client = mock(ActiveSessionClient.class);
        when(client.verify(anyString(), anyString())).thenThrow(new BusinessException(status, "Synthetic authority denial"));
        var exchange = exchange(UUID.randomUUID(), UUID.randomUUID());
        new JwtAuthenticationFilter(new ObjectMapper(), client)
                .filter(exchange, ignored -> { throw new AssertionError("must not route"); }).block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(status);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "role", "session"})
    void wrongAuthorityBindingCannotBeForwarded(String mismatch) {
        UUID user = UUID.randomUUID(), session = UUID.randomUUID();
        var client = mock(ActiveSessionClient.class);
        // Identity rejects mismatched claims/session state; Gateway must preserve that denial.
        when(client.verify(anyString(),anyString())).thenThrow(new BusinessException(401,"Authority binding mismatch: "+mismatch));
        var exchange = exchange(user, session);
        new JwtAuthenticationFilter(new ObjectMapper(), client)
                .filter(exchange, ignored -> { throw new AssertionError("must not route"); }).block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    private MockServerWebExchange exchange(UUID user, UUID session) {
        String token = Jwts.builder().subject(user.toString()).id(session.toString()).issuer("careflow-identity")
                .claim("role", "PATIENT").expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/patients/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token).build());
    }
}
