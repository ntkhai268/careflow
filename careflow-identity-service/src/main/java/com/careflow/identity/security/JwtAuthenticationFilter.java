package com.careflow.identity.security;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Set<String> PUBLIC_POST_PATHS = Set.of(
            "/api/auth/register", "/api/auth/registration/email/request", "/api/auth/registration/email/verify",
            "/api/auth/staff-passkeys/enroll/options", "/api/auth/staff-passkeys/enroll/finish", "/api/auth/mail/events", "/api/auth/login", "/api/auth/login/biometric",
            "/api/auth/recovery/email/request","/api/auth/recovery/email/verify", "/api/auth/recovery/email/confirm",
            "/api/auth/refresh", "/api/auth/logout");
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final DpopProofService dpop;
    private final CallerRegistry callers;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper, DpopProofService dpop, CallerRegistry callers) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.dpop = dpop; this.callers = callers;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith("Bearer ") || authorization.length() == 7) {
            unauthorized(response, "Bearer token không hợp lệ");
            return;
        }

        try {
            JwtService.AuthenticatedUser user = jwtService.parse(authorization.substring(7));
            String caller = request.getHeader("X-Careflow-Caller");
            if ("/api/auth/session".equals(request.getRequestURI()) && caller != null) {
                callers.requireTransport(request,caller);
                callers.require(caller, request.getHeader("X-Careflow-Caller-Key"));
                String method = request.getHeader("X-Careflow-Request-Method");
                if (method != null) {
                    String path = request.getHeader("X-Careflow-Request-Path");
                    String delegate = request.getHeader("X-Careflow-Delegated-Caller");
                    if (delegate != null) callers.requireDelegation(delegate, request.getHeader("X-Careflow-Delegated-Key"), method, path);
                    else dpop.verify(request.getHeader("DPoP"),method,path,authorization.substring(7),user.dpopJkt());
                }
            } else if("POST".equals(request.getMethod()) && "/api/auth/identity-document/confirmed-match".equals(request.getRequestURI()) && "patient-service".equals(caller)){
                callers.requireTransport(request,caller);
                callers.requireDelegation(caller,request.getHeader("X-Careflow-Caller-Key"),request.getMethod(),request.getRequestURI());
            } else if ("GET".equals(request.getMethod()) && request.getRequestURI().matches("/api/users/staff/[0-9a-fA-F-]{36}") && "hospital-directory-service".equals(caller)) {
                callers.requireTransport(request,caller);
                callers.requireDelegation(caller,request.getHeader("X-Careflow-Caller-Key"),request.getMethod(),request.getRequestURI());
            } else {
                dpop.verify(request.getHeader("DPoP"),request.getMethod(),request.getRequestURI(),authorization.substring(7),user.dpopJkt());
            }
            var authentication = new UsernamePasswordAuthenticationToken(
                    user.userId().toString(), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
            authentication.setDetails(user);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            unauthorized(response, "JWT không hợp lệ hoặc đã hết hạn");
        } catch (BusinessException exception) {
            SecurityContextHolder.clearContext();
            response.setStatus(exception.getStatus());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(exception.getStatus(), exception.getMessage()));
        } catch (DataAccessException | TransactionException exception) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(503, "Không thể xác minh trạng thái phiên đăng nhập"));
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return (HttpMethod.GET.matches(request.getMethod()) && path.equals("/api/auth/jwks"))
                || HttpMethod.OPTIONS.matches(request.getMethod())
                || (HttpMethod.POST.matches(request.getMethod()) && PUBLIC_POST_PATHS.contains(path))
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/actuator/health")
                || path.equals("/actuator/info");
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(401, message));
    }
}
