package com.careflow.notification.service;

public class PushSendException extends RuntimeException {
    private final boolean permanent;

    public PushSendException(String message, boolean permanent, Throwable cause) {
        super(message, cause);
        this.permanent = permanent;
    }

    public boolean isPermanent() {
        return permanent;
    }
}
