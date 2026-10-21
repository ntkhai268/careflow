package com.careflow.gateway.security;

import com.careflow.common.security.ActiveSessionClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

final class GatewaySessionTestSupport {
    private GatewaySessionTestSupport() {}

    static ActiveSessionClient activeSession(String secret) {
        var client = mock(ActiveSessionClient.class);
        when(client.verify(anyString(), anyString())).thenAnswer(call -> {
            String authorization = call.getArgument(0);
            try {
            var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                    .requireIssuer("careflow-identity").build().parseSignedClaims(authorization.substring(7)).getPayload();
            if (claims.getSubject()==null || claims.getId()==null || claims.getExpiration()==null || !java.util.Set.of("PATIENT","DOCTOR","STAFF","ADMIN","LAB_TECHNICIAN").contains(claims.get("role",String.class))) throw new IllegalArgumentException();
            return new ActiveSessionClient.Principal(true, UUID.fromString(claims.getSubject()),
                    claims.get("role", String.class), UUID.fromString(claims.getId()), claims.getExpiration().toInstant());
            } catch (RuntimeException invalid) { throw new com.careflow.common.exception.BusinessException(401,"Fixture authority denial"); }
        });
        return client;
    }
}
