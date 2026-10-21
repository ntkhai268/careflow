package com.careflow.common.security;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public class ActiveSessionClient {
    private static final Set<String> ROLES = Set.of("PATIENT", "DOCTOR", "STAFF", "LAB_TECHNICIAN", "ADMIN");
    private final URI endpoint;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final Duration timeout;
    private final String callerName;
    private final String callerKey;

    public ActiveSessionClient(String identityUrl, ObjectMapper mapper, Duration timeout) {
        this(identityUrl, mapper, timeout, "", "");
    }

    public ActiveSessionClient(String identityUrl, ObjectMapper mapper, Duration timeout, String callerName, String callerKey) {
        this(identityUrl,mapper,timeout,callerName,callerKey,null);
    }

    public ActiveSessionClient(String identityUrl,ObjectMapper mapper,Duration timeout,String callerName,String callerKey,javax.net.ssl.SSLContext ssl) {
        this.callerName = callerName; this.callerKey = callerKey;
        this.endpoint = URI.create(identityUrl.replaceAll("/+$", "") + "/api/auth/session");
        this.mapper = mapper;
        this.timeout = timeout;
        var builder=HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER);
        if(ssl!=null)builder.sslContext(ssl);
        this.client=builder.build();
    }

    public Principal verify(String authorization, String correlationId) {
        return verifyRequest(authorization,correlationId,null,null,null,null,null);
    }

    public Principal verifyRequest(String authorization, String correlationId, String proof, String method, String path, String delegatedCaller, String delegatedKey) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() == 7) {
            throw new BusinessException(401, "Thiếu Bearer token");
        }
        var request = HttpRequest.newBuilder(endpoint).timeout(timeout).GET()
                .header("Authorization", authorization).header("Accept", "application/json");
        if (correlationId != null) request.header("X-Correlation-Id", correlationId);
        if (!callerName.isBlank()) request.header("X-Careflow-Caller",callerName).header("X-Careflow-Caller-Key",callerKey);
        if (method != null) {
            request.header("X-Careflow-Request-Method",method).header("X-Careflow-Request-Path",path);
            if (proof != null) request.header("DPoP",proof);
            if (delegatedCaller != null && delegatedKey != null) request.header("X-Careflow-Delegated-Caller",delegatedCaller).header("X-Careflow-Delegated-Key",delegatedKey);
        }
        try {
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                throw new BusinessException(401, "Phiên đăng nhập không còn hợp lệ");
            }
            if (response.statusCode() == 403) throw new BusinessException(403,"Caller service không có quyền gọi endpoint này");
            if (response.statusCode() != 200) throw unavailable();
            ApiResponse<Principal> envelope = mapper.readValue(response.body(), new TypeReference<>() {});
            Principal principal = envelope == null || envelope.getStatus() != 200 ? null : envelope.getData();
            if (principal == null || principal.active() == null || principal.userId() == null
                    || principal.role() == null || !ROLES.contains(principal.role())
                    || principal.sessionId() == null || principal.expiresAt() == null) throw unavailable();
            if (!principal.active() || !principal.expiresAt().isAfter(Instant.now())) {
                throw new BusinessException(401, "Phiên đăng nhập không còn hợp lệ");
            }
            return principal;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(503, "Không thể xác minh phiên với Identity");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Principal(Boolean active, UUID userId, String role, UUID sessionId, Instant expiresAt) {}
}
