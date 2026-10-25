package com.careflow.common.security;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ActiveSessionFilter extends OncePerRequestFilter {
    public static final String VERIFIED_PRINCIPAL_ATTRIBUTE = "careflow.verified-session-principal";
    private final ActiveSessionClient sessions;
    private final ObjectMapper mapper;
    private final boolean allowLoopbackHttp;

    public ActiveSessionFilter(ActiveSessionClient sessions, ObjectMapper mapper) {
        this(sessions,mapper,false);
    }
    public ActiveSessionFilter(ActiveSessionClient sessions,ObjectMapper mapper,boolean allowLoopbackHttp) {
        this.allowLoopbackHttp=allowLoopbackHttp;
        this.sessions = sessions;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId;
        try {
            correlationId = UUID.fromString(request.getHeader(AppConstants.HEADER_CORRELATION_ID)).toString();
        } catch (IllegalArgumentException | NullPointerException exception) {
            correlationId = UUID.randomUUID().toString();
        }
        ActiveSessionClient.Principal principal = null;
        if (!isPublic(request)) {
            try {
                String caller=request.getHeader("X-Careflow-Caller");
                if (caller!=null) ServiceTransportIdentity.require(request,caller,allowLoopbackHttp);
                principal = sessions.verifyRequest(request.getHeader("Authorization"), correlationId,
                        request.getHeader("DPoP"),request.getMethod(),request.getRequestURI(),
                        request.getHeader("X-Careflow-Caller"),request.getHeader("X-Careflow-Caller-Key"));
            } catch (BusinessException exception) {
                response.setStatus(exception.getStatus());
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.setHeader(AppConstants.HEADER_CORRELATION_ID, correlationId);
                mapper.writeValue(response.getOutputStream(), ApiResponse.error(exception.getStatus(), exception.getMessage()));
                return;
            }
        }
        chain.doFilter(new VerifiedRequest(request, principal, correlationId), response);
    }

    private boolean isPublic(HttpServletRequest request) {
        String path = request.getRequestURI();
        return ("GET".equals(request.getMethod()) && (Set.of("/api/directory/departments", "/api/directory/rooms",
                "/api/directory/doctors").contains(path) || path.matches("/api/directory/departments/[^/]+")))
                || ("POST".equals(request.getMethod()) && Set.of("/api/auth/register", "/api/auth/login",
                "/api/auth/refresh", "/api/auth/logout").contains(path));
    }

    private static class VerifiedRequest extends HttpServletRequestWrapper {
        private final ActiveSessionClient.Principal principal;
        private final String correlationId;

        VerifiedRequest(HttpServletRequest request, ActiveSessionClient.Principal principal, String correlationId) {
            super(request);
            this.principal = principal;
            this.correlationId = correlationId;
        }

        @Override
        public Object getAttribute(String name) {
            if (VERIFIED_PRINCIPAL_ATTRIBUTE.equals(name)) return principal;
            return super.getAttribute(name);
        }

        @Override
        public String getHeader(String name) {
            if (AppConstants.HEADER_USER_ID.equalsIgnoreCase(name)) return principal == null ? null : principal.userId().toString();
            if (AppConstants.HEADER_USER_ROLE.equalsIgnoreCase(name)) return principal == null ? null : principal.role();
            if (AppConstants.HEADER_CORRELATION_ID.equalsIgnoreCase(name)) return correlationId;
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (isIdentityHeader(name)) {
                String value = getHeader(name);
                return value == null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(value));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>();
            super.getHeaderNames().asIterator().forEachRemaining(name -> {
                if (!isIdentityHeader(name)) names.add(name);
            });
            if (principal != null) {
                names.add(AppConstants.HEADER_USER_ID);
                names.add(AppConstants.HEADER_USER_ROLE);
            }
            names.add(AppConstants.HEADER_CORRELATION_ID);
            return Collections.enumeration(names);
        }

        private boolean isIdentityHeader(String name) {
            return AppConstants.HEADER_USER_ID.equalsIgnoreCase(name) || AppConstants.HEADER_USER_ROLE.equalsIgnoreCase(name)
                    || AppConstants.HEADER_CORRELATION_ID.equalsIgnoreCase(name);
        }
    }
}
