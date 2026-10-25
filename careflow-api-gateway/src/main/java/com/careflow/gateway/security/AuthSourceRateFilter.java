package com.careflow.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.*;

/** Bounded per-edge source budget, complemented by Identity's shared database budget. */
@Component
public class AuthSourceRateFilter implements GlobalFilter,Ordered {
    private record Window(long minute,AtomicInteger count) {}
    private final ConcurrentHashMap<String,Window> sources=new ConcurrentHashMap<>();
    private final AtomicLong cleaned=new AtomicLong();
    private final int limit;
    public AuthSourceRateFilter(@Value("${security.auth.source-requests-per-minute:120}") int limit) {
        if(limit<1) throw new IllegalArgumentException("Positive source budget required");this.limit=limit;
    }
    @Override public int getOrder() { return -200; }
    @Override public Mono<Void> filter(ServerWebExchange exchange,GatewayFilterChain chain) {
        String path=exchange.getRequest().getPath().value();
        if(!"POST".equals(exchange.getRequest().getMethod().name()) || !path.startsWith("/api/auth/")) return chain.filter(exchange);
        long minute=System.currentTimeMillis()/60000;
        if(cleaned.getAndSet(minute)!=minute) sources.entrySet().removeIf(entry->entry.getValue().minute()<minute);
        // Do not trust X-Forwarded-For supplied by a client. Configure source limits at the ingress as well.
        var remote=exchange.getRequest().getRemoteAddress();String source=remote==null?"unknown":remote.getAddress().getHostAddress();
        if(!sources.containsKey(source) && sources.size()>=10000) return limited(exchange);
        var window=sources.compute(source,(key,current)->current==null || current.minute()!=minute?new Window(minute,new AtomicInteger()):current);
        if(window.count().updateAndGet(value->value>=limit?limit+1:value+1)>limit) return limited(exchange);
        return chain.filter(exchange);
    }
    private Mono<Void> limited(ServerWebExchange exchange) {
        var response=exchange.getResponse();response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().set("Retry-After","60");response.getHeaders().set("Cache-Control","no-store");return response.setComplete();
    }
}
