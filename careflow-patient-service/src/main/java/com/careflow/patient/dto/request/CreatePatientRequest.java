package com.careflow.patient.dto.request;

import com.careflow.patient.model.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePatientRequest {

    @NotNull(message = "userId không được để trống")
    private UUID userId;

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(max = 100, message = "Họ và tên tối đa 100 ký tự")
    private String fullName;

    private LocalDate dateOfBirth;

    private Gender gender;

    @Size(max = 15, message = "Số điện thoại tối đa 15 ký tự")
    private String phone;

    @Size(max = 20, message = "CMND/CCCD tối đa 20 ký tự")
    private String idCardNumber;

    @Size(max = 20, message = "Số BHYT tối đa 20 ký tự")
    private String insuranceNumber;

    @Size(max = 100, message = "Nghề nghiệp tối đa 100 ký tự")
    private String occupation;

    @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
    private String address;
}
