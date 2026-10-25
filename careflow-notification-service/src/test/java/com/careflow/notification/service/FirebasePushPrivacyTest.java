package com.careflow.notification.service;
import com.careflow.notification.domain.Notification;
import com.google.firebase.messaging.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
class FirebasePushPrivacyTest {
    @Test void lockScreenPayloadContainsNoClinicalDataOrResourceRoute() throws Exception {
        var gateway=mock(FirebaseMessaging.class);var sender=new FirebasePushSender(gateway);var n=new Notification();n.setId(UUID.randomUUID());n.setTitle("SENSITIVE_DIAGNOSIS");n.setBody("SENSITIVE_PATIENT");n.setResourceId("SENSITIVE_RESOURCE");n.setActionType("OPEN_PRESCRIPTION");sender.send("SYNTHETIC_TOKEN",n,UUID.randomUUID());
        var capture=ArgumentCaptor.forClass(Message.class);verify(gateway).send(capture.capture());
        var json=new com.google.gson.Gson().toJson(capture.getValue());
        assertThat(json).contains("CareFlow","notificationId","bindingId","300s","apns-expiration").doesNotContain("SENSITIVE_","OPEN_PRESCRIPTION");
    }
    @Test void invalidArgumentDoesNotPermanentlyDisableAValidInstallation() throws Exception {assertPermanent(MessagingErrorCode.INVALID_ARGUMENT,false);}
    @Test void unregisteredTokenIsPermanentlyRejected() throws Exception {assertPermanent(MessagingErrorCode.UNREGISTERED,true);}
    private void assertPermanent(MessagingErrorCode code,boolean expected) throws Exception {
        var gateway=mock(FirebaseMessaging.class);var error=mock(FirebaseMessagingException.class);when(error.getMessagingErrorCode()).thenReturn(code);when(gateway.send(any(Message.class))).thenThrow(error);var n=new Notification();n.setId(UUID.randomUUID());
        assertThatThrownBy(()->new FirebasePushSender(gateway).send("SYNTHETIC_TOKEN",n,UUID.randomUUID())).isInstanceOfSatisfying(PushSendException.class,e->assertThat(e.isPermanent()).isEqualTo(expected));
    }
}
