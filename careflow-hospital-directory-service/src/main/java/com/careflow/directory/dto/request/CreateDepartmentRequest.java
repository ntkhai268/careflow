package com.careflow.directory.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDepartmentRequest {

    @NotBlank(message = "Mã khoa không được để trống")
    private String code;

    @NotBlank(message = "Tên khoa không được để trống")
    private String name;

    private String description;

    @Builder.Default
    private Boolean isActive = true;
}
