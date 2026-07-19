package com.careflow.patient.dto.request;

import com.careflow.patient.model.Gender;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePatientRequest {

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

    @Size(max = 255, message = "URL ảnh tối đa 255 ký tự")
    private String avatarUrl;
}
