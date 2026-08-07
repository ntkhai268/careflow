package com.careflow.directory.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDepartmentRequest {

    @NotBlank(message = "Tên khoa không được để trống")
    private String name;

    private String description;

    private Boolean isActive;
}
