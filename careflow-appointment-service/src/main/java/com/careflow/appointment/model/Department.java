package com.careflow.appointment.model;

import lombok.Getter;

@Getter
public enum Department {
    NOI_TONG_QUAT("10000000-0000-0000-0000-000000000001", "Nội tổng quát", "ROOM-01", "Phòng 01 - Nội tổng quát"),
    NHI("10000000-0000-0000-0000-000000000002", "Nhi", "ROOM-02", "Phòng 02 - Nhi"),
    NGOAI("10000000-0000-0000-0000-000000000003", "Ngoại", "ROOM-03", "Phòng 03 - Ngoại"),
    SAN("10000000-0000-0000-0000-000000000004", "Sản", "ROOM-04", "Phòng 04 - Sản"),
    MAT("10000000-0000-0000-0000-000000000005", "Mắt", "ROOM-05", "Phòng 05 - Mắt"),
    TAI_MUI_HONG("10000000-0000-0000-0000-000000000006", "Tai mũi họng", "ROOM-06", "Phòng 06 - Tai mũi họng"),
    RANG_HAM_MAT("10000000-0000-0000-0000-000000000007", "Răng hàm mặt", "ROOM-07", "Phòng 07 - Răng hàm mặt"),
    DA_LIEU("10000000-0000-0000-0000-000000000008", "Da liễu", "ROOM-08", "Phòng 08 - Da liễu"),
    THAN_KINH("10000000-0000-0000-0000-000000000009", "Thần kinh", "ROOM-21", "Phòng 21 - Lầu 1 khu A"),
    TIM_MACH("10000000-0000-0000-0000-000000000010", "Tim mạch", "ROOM-10", "Phòng 10 - Tim mạch"),
    CO_XUONG_KHOP("10000000-0000-0000-0000-000000000011", "Cơ xương khớp", "ROOM-11", "Phòng 11 - Cơ xương khớp");

    private final java.util.UUID id;
    private final String displayName;
    private final String roomId;
    private final String roomDisplayName;

    Department(String id, String displayName, String roomId, String roomDisplayName) {
        this.id = java.util.UUID.fromString(id);
        this.displayName = displayName;
        this.roomId = roomId;
        this.roomDisplayName = roomDisplayName;
    }

    public static Department fromCode(String value) {
        if (value == null) throw new IllegalArgumentException("department is null");
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if ("NEUROLOGY".equals(normalized)) normalized = THAN_KINH.name();
        return valueOf(normalized);
    }
}
