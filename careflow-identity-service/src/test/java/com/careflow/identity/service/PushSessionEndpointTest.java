package com.careflow.identity.service;
import com.careflow.common.exception.BusinessException;
import com.careflow.identity.controller.PushSessionStatusController;
import com.careflow.identity.security.CallerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class PushSessionEndpointTest {
    final CallerRegistry callers=mock(CallerRegistry.class);final PushSessionStatusService sessions=mock(PushSessionStatusService.class);
    final PushSessionStatusController controller=new PushSessionStatusController(callers,sessions);
    final PushSessionStatusController.Request body=new PushSessionStatusController.Request(UUID.randomUUID(),UUID.randomUUID());
    MockHttpServletRequest request(){var r=new MockHttpServletRequest();r.addHeader("X-Careflow-Caller","notification-service");r.addHeader("X-Careflow-Caller-Key","SYNTHETIC_PRIVATE_CREDENTIAL");return r;}
    @Test void wrongServiceCannotQueryPushSession(){var r=request();r.removeHeader("X-Careflow-Caller");r.addHeader("X-Careflow-Caller","patient-service");assertThatThrownBy(()->controller.status(body,r)).isInstanceOf(BusinessException.class);verifyNoInteractions(callers,sessions);}
    @Test void transportFailureStopsBeforeAnySessionLookup(){var r=request();doThrow(new BusinessException(403,"Synthetic transport mismatch")).when(callers).requireTransport(r,"notification-service");assertThatThrownBy(()->controller.status(body,r)).isInstanceOf(BusinessException.class);verifyNoInteractions(sessions);}
    @Test void transportAndExactScopeRequiredBeforeNonCacheableAnswer(){var r=request();controller.status(body,r);var ordered=inOrder(callers,sessions);ordered.verify(callers).requireTransport(r,"notification-service");ordered.verify(callers).requireDelegation("notification-service","SYNTHETIC_PRIVATE_CREDENTIAL","POST","/internal/notifications/session-status");ordered.verify(sessions).active(body.userId(),body.sessionId());assertThat(controller.status(body,r).getHeaders().getCacheControl()).contains("no-store");}
}
