package com.careflow.appointment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomResponse {
    private String id;
    private String departmentCode;
    private String displayName;
    private String roomType;
    private Boolean isActive;
}
