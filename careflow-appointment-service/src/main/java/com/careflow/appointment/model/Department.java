package com.careflow.appointment.model;

import lombok.Getter;

@Getter
public enum Department {
    NOI_TONG_QUAT("Nội tổng quát"),
    NHI("Nhi"),
    NGOAI("Ngoại"),
    SAN("Sản"),
    MAT("Mắt"),
    TAI_MUI_HONG("Tai mũi họng"),
    RANG_HAM_MAT("Răng hàm mặt"),
    DA_LIEU("Da liễu"),
    THAN_KINH("Thần kinh"),
    TIM_MACH("Tim mạch"),
    CO_XUONG_KHOP("Cơ xương khớp");

    private final String displayName;

    Department(String displayName) {
        this.displayName = displayName;
    }
}
