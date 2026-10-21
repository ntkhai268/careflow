package com.careflow.ai.voice;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class VoiceTypes {
    private VoiceTypes() {}
    public static final String SCHEMA = "careflow.voice-prescription.v1";
    public record TextRequest(@NotNull UUID consultationId, @NotBlank @Size(max=12000) String transcript) {}
    public record RevisionRequest(@Min(1) long expectedVersion, @NotBlank @Size(max=12000) String transcript,
                                  boolean append) {}
    public record Actor(UUID userId, String authorization, String correlationId) {}
    public record Job(UUID jobId, UUID consultationId, UUID actorId, long version, int transcriptRevision,
                      String state, String idempotencyKey, String requestHash, String inputJson,
                      JsonNode result, String errorCode, String correlationId, Instant createdAt, Instant expiresAt) {}
    public record JobResponse(UUID jobId, UUID consultationId, long version, int transcriptRevision,
                              String state, String schemaVersion, JsonNode result, String errorCode,
                              Instant createdAt, Instant expiresAt) {
        public static JobResponse of(Job j) {
            return new JobResponse(j.jobId(),j.consultationId(),j.version(),j.transcriptRevision(),
                    j.state(),SCHEMA,j.result(),j.errorCode(),j.createdAt(),j.expiresAt());
        }
    }
}
