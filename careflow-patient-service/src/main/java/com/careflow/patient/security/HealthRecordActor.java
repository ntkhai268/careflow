package com.careflow.patient.security;

import java.util.UUID;

public record HealthRecordActor(UUID userId, String role) {
    public static final String REQUEST_ATTRIBUTE = "careflow.health-record-actor";
}
