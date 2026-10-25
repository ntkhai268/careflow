package com.careflow.patient.controller;
import com.careflow.common.exception.BusinessException;
import com.careflow.patient.model.Patient;
import com.careflow.patient.repository.PatientRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class InternalPatientOwnerBoundaryTest {
    final String key=UUID.randomUUID()+UUID.randomUUID().toString();
    InternalPatientOwnerController controller(PatientRepository repo)throws Exception{return new InternalPatientOwnerController(repo,new ObjectMapper(),new ObjectMapper().writeValueAsString(Map.of("queue-service",key)));}
    MockHttpServletRequest request(String cn,String suppliedKey){var r=new MockHttpServletRequest();r.setSecure(true);r.addHeader("X-Careflow-Caller","queue-service");r.addHeader("X-Careflow-Caller-Key",suppliedKey);var certificate=mock(X509Certificate.class);when(certificate.getSubjectX500Principal()).thenReturn(new X500Principal("CN="+cn));r.setAttribute("jakarta.servlet.request.X509Certificate",new X509Certificate[]{certificate});return r;}
    @Test void gatewayCertificateCannotImpersonateQueueAndWrongKeyCannotReadOwner()throws Exception{var repo=mock(PatientRepository.class);var c=controller(repo);assertThatThrownBy(()->c.owner(UUID.randomUUID(),request("api-gateway",key))).isInstanceOf(BusinessException.class);assertThatThrownBy(()->c.owner(UUID.randomUUID(),request("queue-service","wrong"))).isInstanceOf(BusinessException.class);verifyNoInteractions(repo);}
    @Test void plainHttpAndMissingClientCertificateFailClosed()throws Exception{var repo=mock(PatientRepository.class);var c=controller(repo);var plain=request("queue-service",key);plain.setSecure(false);assertThatThrownBy(()->c.owner(UUID.randomUUID(),plain)).isInstanceOf(BusinessException.class);var absent=request("queue-service",key);absent.removeAttribute("jakarta.servlet.request.X509Certificate");assertThatThrownBy(()->c.owner(UUID.randomUUID(),absent)).isInstanceOf(BusinessException.class);verifyNoInteractions(repo);}
    @Test void validServiceSeesOnlyCurrentOwnerIncludingNullAfterRevocation()throws Exception{var repo=mock(PatientRepository.class);var c=controller(repo);UUID id=UUID.randomUUID(),owner=UUID.randomUUID();Patient patient=Patient.builder().userId(owner).fullName("Never return these demographics").idCardNumber("000000000001").build();patient.setId(id);when(repo.findById(id)).thenReturn(Optional.of(patient));var json=new ObjectMapper().findAndRegisterModules();String granted=json.writeValueAsString(c.owner(id,request("queue-service",key)).getBody());assertThat(granted).contains(owner.toString()).doesNotContain(patient.getFullName(),patient.getIdCardNumber());patient.setUserId(null);String revoked=json.writeValueAsString(c.owner(id,request("queue-service",key)).getBody());assertThat(revoked).contains("\"userId\":null").doesNotContain(owner.toString());}
}
