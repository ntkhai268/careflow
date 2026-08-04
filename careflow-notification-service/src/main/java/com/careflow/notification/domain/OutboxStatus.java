package com.careflow.notification.domain;

public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED,
    DEAD
}
