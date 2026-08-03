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
    public void send(String registrationToken, Notification notification) {
        Message.Builder builder = Message.builder()
                .setToken(registrationToken)
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(notification.getTitle())
                        .setBody(notification.getBody())
                        .build())
                .putData("notificationId", notification.getId().toString())
                .putData("type", notification.getType().name())
                .putData("actionType", value(notification.getActionType()))
                .putData("resourceId", value(notification.getResourceId()))
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId("careflow_notifications")
                                .setClickAction("FLUTTER_NOTIFICATION_CLICK")
                                .build())
                        .build());
        try {
            messaging.send(builder.build());
        } catch (FirebaseMessagingException exception) {
            MessagingErrorCode code = exception.getMessagingErrorCode();
            boolean permanent = code == MessagingErrorCode.UNREGISTERED
                    || code == MessagingErrorCode.INVALID_ARGUMENT
                    || code == MessagingErrorCode.SENDER_ID_MISMATCH;
            throw new PushSendException("FCM rejected push: " + exception.getMessage(), permanent, exception);
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
