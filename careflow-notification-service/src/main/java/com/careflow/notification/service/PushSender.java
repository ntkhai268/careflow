package com.careflow.notification.service;

import com.careflow.notification.domain.Notification;

public interface PushSender {
    void send(String registrationToken, Notification notification);
}
