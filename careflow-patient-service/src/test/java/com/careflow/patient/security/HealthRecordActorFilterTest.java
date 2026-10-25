package com.careflow.patient.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class HealthRecordActorFilterTest {
    @Test
    void forgedHeadersCannotCreateAnActorWithoutVerifiedCommonSessionContext() throws Exception {
        var request=new MockHttpServletRequest("GET","/api/patients/00000000-0000-0000-0000-000000000001/health-records");
        request.addHeader("X-User-Id","00000000-0000-0000-0000-000000000002");
        request.addHeader("X-User-Role","ADMIN");
        var response=new MockHttpServletResponse();var executed=new AtomicBoolean();
        new HealthRecordActorFilter(new ObjectMapper().findAndRegisterModules()).doFilter(request,response,(req,res)->executed.set(true));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(executed).isFalse();
        assertThat(request.getAttribute(HealthRecordActor.REQUEST_ATTRIBUTE)).isNull();
    }
}
