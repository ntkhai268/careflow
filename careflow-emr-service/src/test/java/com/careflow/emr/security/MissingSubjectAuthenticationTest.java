package com.careflow.emr.security;

import com.careflow.common.security.ActiveSessionClient;
import com.careflow.common.security.ActiveSessionFilter;
import com.careflow.common.exception.BusinessException;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class MissingSubjectAuthenticationTest {
    @Test
    void signedTokenWithoutSubjectReturns401AndDoesNotExecuteRequest() throws Exception {
        String token = Jwts.builder().issuer("careflow-identity").claim("role", "DOCTOR")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Jwts.SIG.RS256.keyPair().build().getPrivate()).compact();
        ActiveSessionClient sessions = mock(ActiveSessionClient.class);
        when(sessions.verifyRequest(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(401, "Identity rejected missing subject"));
        var filter = new ActiveSessionFilter(sessions, new ObjectMapper().findAndRegisterModules());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/emr/records");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean executed = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> executed.set(true));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(executed).isFalse();
        assertThat(response.getContentAsString()).contains("401");
    }
}
