package com.careflow.ai.controller;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Clinical AI cannot be enabled in secure deployments until its authorization contract exists. */
@Component
public class AiAvailabilityFilter extends OncePerRequestFilter {
    private final boolean enabled;
    public AiAvailabilityFilter(Environment environment,@Value("${security.ai.demo-enabled:false}") boolean demoEnabled) {
        enabled=demoEnabled && !environment.matchesProfiles("secure","prod","production");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        if(request.getRequestURI().startsWith("/api/ai") && !enabled) {
            response.setStatus(503);response.setHeader("Cache-Control","no-store");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":503,\"message\":\"Tính năng AI chưa sẵn sàng cho dữ liệu thật.\"}");return;
        }
        chain.doFilter(request,response);
    }
}
