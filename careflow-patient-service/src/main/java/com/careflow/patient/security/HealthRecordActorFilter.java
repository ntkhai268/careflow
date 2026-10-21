package com.careflow.patient.security;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.security.ActiveSessionClient;
import com.careflow.common.security.ActiveSessionFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Adapts Common's verified principal to the existing Health Records ownership API. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class HealthRecordActorFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;
    public HealthRecordActorFilter(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equals(request.getMethod())
                || !request.getRequestURI().matches("/api/patients/[^/]+/health-records(?:/.*)?");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!(request.getAttribute(ActiveSessionFilter.VERIFIED_PRINCIPAL_ATTRIBUTE)
                instanceof ActiveSessionClient.Principal principal) || !Boolean.TRUE.equals(principal.active())) {
            response.setStatus(401);
            response.setContentType("application/json");
            mapper.writeValue(response.getOutputStream(), ApiResponse.error(401, "Authenticated user is required"));
            return;
        }
        request.setAttribute(HealthRecordActor.REQUEST_ATTRIBUTE, new HealthRecordActor(principal.userId(), principal.role()));
        chain.doFilter(request, response);
    }
}
