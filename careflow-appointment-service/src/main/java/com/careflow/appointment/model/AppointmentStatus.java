package com.careflow.appointment.model;

import lombok.Getter;

@Getter
public enum AppointmentStatus {
    PENDING("Chờ xác nhận"),
    CONFIRMED("Đã xác nhận"),
    CHECKED_IN("Đã check-in"),
    IN_PROGRESS("Đang khám"),
    COMPLETED("Hoàn thành"),
    CANCELLED("Đã hủy");

    private final String displayName;

    AppointmentStatus(String displayName) {
        this.displayName = displayName;
    }

    public boolean canTransitionTo(AppointmentStatus next) {
        return switch (this) {
            case PENDING -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == CHECKED_IN || next == IN_PROGRESS || next == CANCELLED;
            case CHECKED_IN -> next == IN_PROGRESS || next == CANCELLED;
            case IN_PROGRESS -> next == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    public boolean isActiveOrCompleted() {
        return this == IN_PROGRESS || this == COMPLETED;
    }
}
