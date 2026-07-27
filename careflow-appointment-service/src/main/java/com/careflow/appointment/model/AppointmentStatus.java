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
}
