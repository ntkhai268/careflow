package com.careflow.directory.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateRoomRequest {

    @NotBlank(message = "Mã phòng không được để trống")
    private String id;

    @NotBlank(message = "Mã khoa không được để trống")
    private String departmentCode;

    @NotBlank(message = "Tên phòng hiển thị không được để trống")
    private String displayName;

    @NotBlank(message = "Loại phòng không được để trống")
    private String roomType;

    @Builder.Default
    private Boolean isActive = true;
}
