package com.careflow.notification.security;
import com.careflow.common.security.ActiveSessionClient;
import com.careflow.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class JwtAuthenticatorTest {
    @Test void usesOnlyVerifiedIdentity() {
        var sessions=mock(ActiveSessionClient.class);UUID user=UUID.randomUUID();
        when(sessions.verify("Bearer fixture",null)).thenReturn(new ActiveSessionClient.Principal(true,user,"LAB_TECHNICIAN",UUID.randomUUID(),Instant.now().plusSeconds(60)));
        var auth=new JwtAuthenticator(sessions).authenticate("Bearer fixture");
        assertThat(auth.getName()).isEqualTo(user.toString());
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_LAB_TECHNICIAN");
    }
    @Test void unavailableOrRevokedAuthorityCannotAuthenticate() {
        var sessions=mock(ActiveSessionClient.class);
        for(int status:new int[]{401,503}) {
            doThrow(new BusinessException(status,"Denied")).when(sessions).verify("Bearer fixture",null);
            assertThatThrownBy(()->new JwtAuthenticator(sessions).authenticate("Bearer fixture")).isInstanceOf(BusinessException.class);
        }
    }
    @Test void connectValidatesProofForNotificationEndpoint() {
        var sessions=mock(ActiveSessionClient.class);UUID user=UUID.randomUUID();
        when(sessions.verifyRequest("Bearer fixture",null,"proof","GET","/ws/notifications",null,null))
            .thenReturn(new ActiveSessionClient.Principal(true,user,"PATIENT",UUID.randomUUID(),Instant.now().plusSeconds(60)));
        assertThat(new JwtAuthenticator(sessions).authenticateConnect("Bearer fixture","proof").getName()).isEqualTo(user.toString());
    }
}
