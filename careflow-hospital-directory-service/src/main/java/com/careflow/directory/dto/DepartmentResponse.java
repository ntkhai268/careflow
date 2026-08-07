package com.careflow.directory.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class DepartmentResponse {
    private String code;
    private UUID id;
    private String name;
    private String description;
    private Boolean isActive;
}
