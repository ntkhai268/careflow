package com.careflow.directory.service;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.*;
import org.springframework.web.context.request.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffIdentityVerifierTest {
    RestTemplate client;
    StaffIdentityVerifier identity;
    final UUID userId=UUID.randomUUID();
    @BeforeEach void fixture(){
        var builder=mock(RestTemplateBuilder.class,RETURNS_SELF);client=mock(RestTemplate.class);when(builder.build()).thenReturn(client);
        identity=new StaffIdentityVerifier(builder,"https://identity.internal");
        var request=new MockHttpServletRequest();request.addHeader("Authorization","Bearer actor-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    @AfterEach void clean(){RequestContextHolder.resetRequestAttributes();}
    @Test void checksActualIdentityIdRoleAndStatusAndForwardsOnlyActorToken(){
        when(client.exchange(eq("https://identity.internal/api/users/staff/"+userId),eq(HttpMethod.GET),any(HttpEntity.class),any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(ApiResponse.success(new StaffIdentityVerifier.Account(userId,"DOCTOR","ACTIVE"))));
        identity.require(userId,Set.of("DOCTOR"),true);
        verify(client).exchange(anyString(),eq(HttpMethod.GET),argThat((HttpEntity<?> entity)->"Bearer actor-token".equals(entity.getHeaders().getFirst("Authorization")) && entity.getHeaders().getFirst("DPoP")==null),any(org.springframework.core.ParameterizedTypeReference.class));
    }
    @ParameterizedTest @CsvSource({"STAFF,ACTIVE","DOCTOR,LOCKED","DOCTOR,DISABLED"})
    void wrongRoleOrInactiveUserCannotBeAssigned(String role,String status){
        when(client.exchange(anyString(),eq(HttpMethod.GET),any(HttpEntity.class),any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(ApiResponse.success(new StaffIdentityVerifier.Account(userId,role,status))));
        assertThatThrownBy(()->identity.require(userId,Set.of("DOCTOR"),true)).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(409);
    }
    @Test void anotherProfileIdCannotSubstituteForIdentityId(){
        when(client.exchange(anyString(),eq(HttpMethod.GET),any(HttpEntity.class),any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(ApiResponse.success(new StaffIdentityVerifier.Account(UUID.randomUUID(),"DOCTOR","ACTIVE"))));
        assertThatThrownBy(()->identity.require(userId,Set.of("DOCTOR"),true)).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(503);
    }
    @Test void identityOutageFailsClosed(){
        when(client.exchange(anyString(),eq(HttpMethod.GET),any(HttpEntity.class),any(org.springframework.core.ParameterizedTypeReference.class))).thenThrow(new ResourceAccessException("Synthetic outage"));
        assertThatThrownBy(()->identity.require(userId,Set.of("DOCTOR"),true)).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(503);
    }
    @Test void absentActorNeverCallsIdentity(){
        RequestContextHolder.resetRequestAttributes();
        assertThatThrownBy(()->identity.require(userId,Set.of("DOCTOR"),true)).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(401);
        verifyNoInteractions(client);
    }
}
