package com.careflow.identity.security;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffDirectoryDelegationTest {
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    JwtService jwt=mock(JwtService.class);
    DpopProofService dpop=mock(DpopProofService.class);
    CallerRegistry callers=mock(CallerRegistry.class);
    String path="/api/users/staff/"+UUID.randomUUID();
    MockHttpServletRequest request(String method,String path){
        when(jwt.parse("token")).thenReturn(new JwtService.AuthenticatedUser(UUID.randomUUID(),UserRole.ADMIN,UUID.randomUUID(),Instant.now().plusSeconds(60),"a".repeat(43)));
        var request=new MockHttpServletRequest(method,path);request.addHeader("Authorization","Bearer token");
        request.addHeader("X-Careflow-Caller","hospital-directory-service");request.addHeader("X-Careflow-Caller-Key","private-fixture-key");return request;
    }
    @Test void narrowReadRequiresTransportAndDelegationWithoutReusingBrowserProof() throws Exception {
        var request=request("GET",path);var response=new MockHttpServletResponse();
        new JwtAuthenticationFilter(jwt,new ObjectMapper().findAndRegisterModules(),dpop,callers).doFilter(request,response,(req,res)->{});
        verify(callers).requireTransport(request,"hospital-directory-service");
        verify(callers).requireDelegation("hospital-directory-service","private-fixture-key","GET",path);
        verifyNoInteractions(dpop);assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }
    @ParameterizedTest @ValueSource(strings={"transport","scope"})
    void rejectedServiceCannotReachController(String failure) throws Exception {
        var request=request("GET",path);var response=new MockHttpServletResponse();
        if(failure.equals("transport"))doThrow(new BusinessException(401,"Invalid TLS identity")).when(callers).requireTransport(any(),anyString());
        else doThrow(new BusinessException(403,"Missing scope")).when(callers).requireDelegation(anyString(),anyString(),anyString(),anyString());
        new JwtAuthenticationFilter(jwt,new ObjectMapper().findAndRegisterModules(),dpop,callers).doFilter(request,response,(req,res)->{throw new AssertionError("Denied request reached controller");});
        assertThat(response.getStatus()).isEqualTo(failure.equals("transport")?401:403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"GET /api/users/staff","POST /api/users/staff","PATCH /api/users/11111111-1111-1111-1111-111111111111/status"})
    void serviceReadExceptionDoesNotAuthorizeListCreateOrMutation(String operation) throws Exception {
        var parts=operation.split(" ");var request=request(parts[0],parts[1]);var response=new MockHttpServletResponse();
        when(dpop.verify(any(),anyString(),anyString(),anyString(),anyString())).thenThrow(new BusinessException(401,"Proof required"));
        new JwtAuthenticationFilter(jwt,new ObjectMapper().findAndRegisterModules(),dpop,callers).doFilter(request,response,(req,res)->{throw new AssertionError("Must require the user's proof");});
        assertThat(response.getStatus()).isEqualTo(401);verifyNoInteractions(callers);
    }
}
