package com.careflow.notification.service;

import com.careflow.notification.domain.Notification;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "notification.push.firebase", name = "enabled", havingValue = "true")
public class FirebasePushSender implements PushSender {
    private final FirebaseMessaging messaging;

    public FirebasePushSender(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    @Override
    public void send(String registrationToken, Notification notification, java.util.UUID bindingId) {
        Message.Builder builder = Message.builder()
                .setToken(registrationToken)
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle("CareFlow")
                        .setBody("Bạn có thông báo mới. Mở CareFlow để xem chi tiết.")
                        .build())
                .putData("notificationId", notification.getId().toString())
                .putData("bindingId", bindingId.toString())
                .setApnsConfig(com.google.firebase.messaging.ApnsConfig.builder().setAps(com.google.firebase.messaging.Aps.builder().setSound("default").build()).putHeader("apns-expiration",String.valueOf(java.time.Instant.now().plusSeconds(300).getEpochSecond())).build())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setTtl(300_000L)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId("careflow_notifications")
                                .setClickAction("FLUTTER_NOTIFICATION_CLICK")
                                .build())
                        .build());
        try {
            messaging.send(builder.build());
        } catch (FirebaseMessagingException exception) {
            MessagingErrorCode code = exception.getMessagingErrorCode();
            boolean permanent = code == MessagingErrorCode.UNREGISTERED;
            throw new PushSendException("FCM_" + (code==null?"UNKNOWN":code.name()), permanent, exception);
        }
    }

}
