package com.careflow.patient.model;

import lombok.Getter;

@Getter
public enum AllergySeverity {
    CRITICAL("Nguy hiểm cao (Phản vệ / Cảnh báo nặng)"),
    WARNING("Cần lưu ý (Dị ứng nhẹ / Tương tác)"),
    INFO("Thông tin nền (Ghi chú / Tiền sử)");

    private final String description;

    AllergySeverity(String description) {
        this.description = description;
    }
}
