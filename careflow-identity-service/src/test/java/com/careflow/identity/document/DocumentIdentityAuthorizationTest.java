package com.careflow.identity.document;
import com.careflow.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class DocumentIdentityAuthorizationTest {
    final DocumentIdentityService service=mock(DocumentIdentityService.class);
    final DocumentIdentityController controller=new DocumentIdentityController(service);
    UsernamePasswordAuthenticationToken auth(String role) { return new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(),"",List.of(new SimpleGrantedAuthority("ROLE_"+role))); }
    @Test void patientCannotReadQueueDetailsOrReview() {
        var patient=auth("PATIENT");var id=UUID.randomUUID();
        assertThatThrownBy(()->controller.pending(patient,0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->controller.detail(patient,id)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->controller.review(patient,id,new DocumentIdentityController.Review(true,true,true,true,"DESK-01"))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(service);
    }
    @Test void doctorAndLabCannotInspectOrApproveIdentity() {
        for(String role:List.of("DOCTOR","LAB_TECHNICIAN")) {
            assertThatThrownBy(()->controller.pending(auth(role),0)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(()->controller.detail(auth(role),UUID.randomUUID())).isInstanceOf(BusinessException.class);
        }verifyNoInteractions(service);
    }
    @Test void staffAndAdminCanReadQueueButCannotSubmitAsPatient() {
        for(String role:List.of("STAFF","ADMIN")) {
            controller.pending(auth(role),0);
            assertThatThrownBy(()->controller.submit(auth(role),UUID.randomUUID(),new DocumentIdentityController.Submission("000000000001","TEST PERSON","1990-01-02",true))).isInstanceOf(BusinessException.class);
        }verify(service,times(2)).pending(0);
    }
    @Test void anonymousAndRetiredProviderAreBlocked() {
        assertThatThrownBy(()->controller.status(null)).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(401);
        assertThatThrownBy(()->new RetiredEkycController().retired()).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(410);
    }
}
