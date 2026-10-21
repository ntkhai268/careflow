package com.careflow.consultation.handover;
import jakarta.validation.constraints.*;
import java.util.UUID;
public final class HandoverRequests {
    private HandoverRequests() {}
    public record Create(@NotNull UUID consultationId, @NotNull UUID toDoctorId,
        @NotBlank @Size(max=2000) String reason,
        @NotBlank @Size(max=4000) String situation,
        @NotBlank @Size(max=4000) String background,
        @NotBlank @Size(max=4000) String assessment,
        @NotBlank @Size(max=4000) String recommendation) {}
    public record Decision(@NotBlank @Size(max=2000) String note) {}
    public record Grant(@NotNull UUID doctorId, @NotBlank @Size(max=2000) String reason) {}
}
