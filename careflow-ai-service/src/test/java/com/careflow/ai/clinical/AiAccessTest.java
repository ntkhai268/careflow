package com.careflow.ai.clinical;

import com.careflow.common.exception.BusinessException;
import com.careflow.common.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AiAccessTest {
    HttpServer server;AiAccess access;ObjectMapper json=new ObjectMapper();
    UUID actor=UUID.randomUUID(),patient=UUID.randomUUID(),visit=UUID.randomUUID();
    String patientBody,consultationBody;int sourceStatus=200;
    @BeforeEach void start()throws Exception{
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        patientBody="{\"id\":\""+patient+"\",\"fullName\":\"PRIVATE_NAME\",\"email\":\"PRIVATE_CONTACT\",\"allergyNotes\":\"TEST_ALLERGY\",\"dateOfBirth\":\"1990-01-01\"}";
        consultationBody="{\"id\":\""+visit+"\",\"patientId\":\""+patient+"\",\"doctorId\":\""+actor+"\",\"departmentCode\":\"NOI_TONG_QUAT\",\"status\":\"IN_PROGRESS\",\"symptoms\":\"TEST_SYMPTOM\"}";
        server.createContext("/api",e->{byte[] body=("{\"data\":"+(e.getRequestURI().getPath().startsWith("/api/patients")?patientBody:consultationBody)+"}").getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(sourceStatus,body.length);try(var out=e.getResponseBody()){out.write(body);}});server.start();
        String base="http://127.0.0.1:"+server.getAddress().getPort();
        access=new AiAccess(json,new DefaultListableBeanFactory().getBeanProvider(InternalTlsMaterial.class),base,base,"ai-service","fixture-only");
    }
    @AfterEach void stop(){server.stop(0);}
    @Test void contextReadsAuthoritativeSourcesAndExcludesIdentityAndContactFields(){var c=access.context(visit,new AiAccess.Actor(actor,"Bearer fixture"));assertThat(c.department()).isEqualTo("NOI_TONG_QUAT");assertThat(c.facts().path("allergyNotes").asText()).isEqualTo("TEST_ALLERGY");assertThat(c.facts().toString()).doesNotContain("PRIVATE_NAME","PRIVATE_CONTACT",patient.toString(),visit.toString());}
    @Test void wrongPatientLinkAndChangedAssignmentFailClosed(){patientBody=patientBody.replace(patient.toString(),UUID.randomUUID().toString());assertThatThrownBy(()->access.context(visit,new AiAccess.Actor(actor,"Bearer fixture"))).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(503));assertThatThrownBy(()->access.context(visit,new AiAccess.Actor(UUID.randomUUID(),"Bearer fixture"))).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(403));}
    @Test void dependencyOutageDoesNotBecomeAnEmptyMedicalHistory(){sourceStatus=503;assertThatThrownBy(()->access.context(visit,new AiAccess.Actor(actor,"Bearer fixture"))).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(503));}
    @Test void spoofedRoleHeadersDoNotCreateAValidPrincipal(){var request=new MockHttpServletRequest();request.addHeader("X-User-Role","DOCTOR");assertThatThrownBy(()->access.doctor(request)).isInstanceOf(BusinessException.class);request.setAttribute(ActiveSessionFilter.VERIFIED_PRINCIPAL_ATTRIBUTE,new ActiveSessionClient.Principal(true,actor,"DOCTOR",UUID.randomUUID(),Instant.now().minusSeconds(1)));assertThatThrownBy(()->access.doctor(request)).isInstanceOf(BusinessException.class);}
}
