package com.careflow.patient.dto.request;

import com.careflow.patient.model.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** No userId field: receiving staff never becomes the patient's owner. */
public record ReceptionPatientRequest(
        @NotBlank @Size(max = 50) String roomId,
        @NotBlank @Size(max = 100) String fullName,
        @PastOrPresent LocalDate dateOfBirth,
        Gender gender,
        @Pattern(regexp="^$|^\\+?[0-9]{9,15}$") @Size(max = 15) String phone,
        @Pattern(regexp="^$|^[0-9]{12}$") String idCardNumber) {}
