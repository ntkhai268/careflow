package com.careflow.consultation.dto.request;

import com.careflow.consultation.model.ConsultationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateConsultationStatusRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private ConsultationStatus status;
}
