package com.careflow.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import java.net.InetSocketAddress;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthSourceRateFilterTest {
    @Test void forgedForwardedHeadersCannotResetSourceBudget() {
        var filter=new AuthSourceRateFilter(1);
        var chain=mock(org.springframework.cloud.gateway.filter.GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(reactor.core.publisher.Mono.empty());
        var first=exchange("192.0.2.1","198.51.100.1");filter.filter(first,chain).block();
        var second=exchange("192.0.2.1","198.51.100.2");filter.filter(second,chain).block();
        assertThat(second.getResponse().getStatusCode().value()).isEqualTo(429);verify(chain,times(1)).filter(any());
        filter.filter(exchange("192.0.2.2","198.51.100.2"),chain).block();verify(chain,times(2)).filter(any());
    }
    private MockServerWebExchange exchange(String source,String forwarded) {
        return MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/login")
            .remoteAddress(new InetSocketAddress(source,1200)).header("X-Forwarded-For",forwarded).build());
    }
}
