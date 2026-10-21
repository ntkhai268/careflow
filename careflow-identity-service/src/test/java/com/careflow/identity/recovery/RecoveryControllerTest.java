package com.careflow.identity.recovery;

import com.careflow.identity.controller.LoginMethodsController;
import com.careflow.identity.security.DpopProofService;
import com.careflow.identity.service.LoginMethodsService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.careflow.identity.recovery.PasswordRecoveryServiceTest.assertStatus;

class RecoveryControllerTest {
    @Test void legacyResetRoutesCannotInvokeOldRecoveryServices() throws Exception {
        var methods=mock(LoginMethodsService.class);var controller=new LoginMethodsController(methods,mock(DpopProofService.class));
        var request=new MockHttpServletRequest();
        assertStatus(410,()->controller.reset(new LoginMethodsController.QuestionReset("patient","memory","answer","ValidPassword1!"),request));
        assertThatThrownBy(()->controller.recoveryOptions(new LoginMethodsController.Start("patient"),request))
            .isInstanceOfSatisfying(com.careflow.common.exception.BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(410));
        assertStatus(410,()->controller.recoveryFinish(new LoginMethodsController.ResetFinish(UUID.randomUUID(),"credential","ValidPassword1!"),request));
        verifyNoInteractions(methods);
    }
    @Test void emailRoutesRequireFreshAnonymousDeviceProofAndReturnNoStore() {
        var service=mock(PasswordRecoveryService.class);var proofs=mock(DpopProofService.class);
        var controller=new PasswordRecoveryController(service,proofs);var http=new MockHttpServletRequest("POST","/api/auth/recovery/email/request");
        http.addHeader("DPoP","signed-proof");
        when(proofs.verify("signed-proof","POST",http.getRequestURI(),null,null)).thenReturn("device-key");
        when(service.request("patient@example.com","device-key")).thenReturn(new PasswordRecoveryService.RequestResult(UUID.randomUUID(),300,60,"generic"));
        var response=controller.request(new PasswordRecoveryController.Request("patient@example.com"),http);
        assertThat(response.getStatusCode().value()).isEqualTo(202);assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        verify(service).request("patient@example.com","device-key");
    }
    @Test void requestAndConfirmationRejectMalformedInputAndWeakPasswords() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            assertThat(validator.validate(new PasswordRecoveryController.Request("not-an-email"))).isNotEmpty();
            for(String password:new String[]{"short","alllowercase1!","ALLUPPERCASE1!","MissingNumber!","NoSpecial123"})
                assertThat(validator.validate(new PasswordRecoveryController.Confirm(UUID.randomUUID(),"x".repeat(43),password))).isNotEmpty();
            assertThat(validator.validate(new PasswordRecoveryController.Confirm(UUID.randomUUID(),"short","GoodPassword123!"))).isNotEmpty();
            assertThat(validator.validate(new PasswordRecoveryController.Confirm(UUID.randomUUID(),"x".repeat(43),"GoodPassword123!"))).isEmpty();
        }
    }
}
