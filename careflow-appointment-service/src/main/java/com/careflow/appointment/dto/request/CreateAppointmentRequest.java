package com.careflow.appointment.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateAppointmentRequest {

    @NotNull(message = "patientId không được để trống")
    private UUID patientId;

    @NotBlank(message = "Tên bệnh nhân không được để trống")
    private String patientName;

    @NotBlank(message = "Chuyên khoa không được để trống")
    private String department;

    @NotNull(message = "Ngày khám không được để trống")
    @FutureOrPresent(message = "Ngày khám phải từ hôm nay trở đi")
    private LocalDate appointmentDate;

    @NotBlank(message = "Ca khám không được để trống")
    private String timeSlot;

    private String reason;
}
