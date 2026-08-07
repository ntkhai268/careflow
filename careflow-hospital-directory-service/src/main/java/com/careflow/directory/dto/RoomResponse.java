package com.careflow.directory.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoomResponse {
    private String id;
    private String departmentCode;
    private String displayName;
    private String roomType;
    private Boolean isActive;
}
