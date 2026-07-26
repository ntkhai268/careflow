package com.careflow.consultation.model;

import lombok.Getter;

@Getter
public enum ConsultationStatus {
    IN_PROGRESS("Đang khám ban đầu"),
    AWAITING_CLS("Chờ làm Cận lâm sàng"),
    AWAITING_REVIEW("Chờ duyệt kết quả CLS"),
    READY_TO_COMPLETE("Sẵn sàng hoàn tất"),
    COMPLETED("Hoàn tất ca khám"),
    CANCELLED("Đã hủy"),
    TRANSFERRED("Chuyển viện / Chuyển khoa");

    private final String displayName;

    ConsultationStatus(String displayName) {
        this.displayName = displayName;
    }
}
