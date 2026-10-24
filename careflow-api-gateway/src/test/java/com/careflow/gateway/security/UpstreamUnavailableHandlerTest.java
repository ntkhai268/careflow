package com.careflow.gateway.security;

import com.careflow.gateway.error.UpstreamUnavailableHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import static org.assertj.core.api.Assertions.*;

class UpstreamUnavailableHandlerTest {
    private final UpstreamUnavailableHandler handler = new UpstreamUnavailableHandler(new ObjectMapper().findAndRegisterModules());
    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/ai/capabilities"));
    }

    @Test void refusedConnectionReturns503WithoutInfrastructureDetails() {
        var exchange = exchange();
        handler.handle(exchange, new RuntimeException(new ConnectException("localhost:18093 private-detail"))).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("503").doesNotContain("localhost", "18093", "private-detail");
    }

    @Test void dnsFailureReturns503() {
        var exchange = exchange();
        handler.handle(exchange, new UnknownHostException("private-service")).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test void authorizationAndApplicationErrorsKeepTheirOriginalHandling() {
        for (var error : new RuntimeException[]{new ResponseStatusException(HttpStatus.FORBIDDEN), new IllegalStateException("application-error")}) {
            assertThatThrownBy(() -> handler.handle(exchange(), error).block()).isSameAs(error);
        }
    }

    @Test void committedResponsesAreNeverOverwritten() {
        var exchange = exchange();
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().setComplete().block();
        var error = new ConnectException("unavailable");
        assertThatThrownBy(() -> handler.handle(exchange, error).block()).hasCause(error);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
