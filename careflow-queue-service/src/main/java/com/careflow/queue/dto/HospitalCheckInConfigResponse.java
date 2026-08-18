package com.careflow.queue.dto;

import com.careflow.queue.domain.HospitalCheckInConfig;

import java.time.Instant;
import java.util.UUID;

public record HospitalCheckInConfigResponse(
        UUID id,
        String siteId,
        String facilityName,
        double latitude,
        double longitude,
        double radiusMeters,
        double maxAccuracyMeters,
        Instant updatedAt) {

    public static HospitalCheckInConfigResponse from(HospitalCheckInConfig config) {
        return new HospitalCheckInConfigResponse(
                config.getId(),
                config.getSiteId(),
                config.getFacilityName(),
                config.getLatitude(),
                config.getLongitude(),
                config.getRadiusMeters(),
                config.getMaxAccuracyMeters(),
                config.getUpdatedAt());
    }
}
