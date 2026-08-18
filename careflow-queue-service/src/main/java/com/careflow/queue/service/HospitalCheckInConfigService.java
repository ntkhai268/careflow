package com.careflow.queue.service;

import com.careflow.queue.domain.HospitalCheckInConfig;
import com.careflow.queue.dto.HospitalCheckInConfigRequest;
import com.careflow.queue.dto.HospitalCheckInConfigResponse;
import com.careflow.queue.repository.HospitalCheckInConfigRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HospitalCheckInConfigService {
    private static final String DEFAULT_SITE_ID = "HOSPITAL-MAIN";
    private static final String DEFAULT_FACILITY_NAME = "Bệnh viện CareFlow";
    private static final double DEFAULT_LATITUDE = 10.7769;
    private static final double DEFAULT_LONGITUDE = 106.7009;
    private static final double DEFAULT_RADIUS_METERS = 150;
    private static final double DEFAULT_MAX_ACCURACY_METERS = 50;

    private final HospitalCheckInConfigRepository repository;
    private final String defaultSiteId;
    private final String defaultFacilityName;
    private final double defaultLatitude;
    private final double defaultLongitude;
    private final double defaultRadiusMeters;
    private final double defaultMaxAccuracyMeters;

    public HospitalCheckInConfigService(
            HospitalCheckInConfigRepository repository,
            @Value("${queue.geofence.site-id:" + DEFAULT_SITE_ID + "}") String defaultSiteId,
            @Value("${queue.geofence.facility-name:" + DEFAULT_FACILITY_NAME + "}") String defaultFacilityName,
            @Value("${queue.geofence.latitude:" + DEFAULT_LATITUDE + "}") double defaultLatitude,
            @Value("${queue.geofence.longitude:" + DEFAULT_LONGITUDE + "}") double defaultLongitude,
            @Value("${queue.geofence.radius-meters:" + DEFAULT_RADIUS_METERS + "}") double defaultRadiusMeters,
            @Value("${queue.geofence.max-accuracy-meters:" + DEFAULT_MAX_ACCURACY_METERS + "}") double defaultMaxAccuracyMeters) {
        this.repository = repository;
        this.defaultSiteId = defaultSiteId;
        this.defaultFacilityName = defaultFacilityName;
        this.defaultLatitude = defaultLatitude;
        this.defaultLongitude = defaultLongitude;
        this.defaultRadiusMeters = requirePositive(defaultRadiusMeters, "radius");
        this.defaultMaxAccuracyMeters = requirePositive(defaultMaxAccuracyMeters, "accuracy");
    }

    @Transactional(readOnly = true)
    public HospitalCheckInConfigResponse get() {
        return HospitalCheckInConfigResponse.from(currentEntity());
    }

    @Transactional
    public HospitalCheckInConfigResponse save(HospitalCheckInConfigRequest request) {
        HospitalCheckInConfig config = repository.findFirstByOrderByUpdatedAtDesc()
                .orElseGet(this::defaultEntity);
        config.setSiteId(request.siteId().trim().toUpperCase());
        config.setFacilityName(request.facilityName().trim());
        config.setLatitude(request.latitude());
        config.setLongitude(request.longitude());
        config.setRadiusMeters(requirePositive(request.radiusMeters(), "radius"));
        config.setMaxAccuracyMeters(requirePositive(request.maxAccuracyMeters(), "accuracy"));
        validate(config);
        return HospitalCheckInConfigResponse.from(repository.save(config));
    }

    @Transactional
    public void ensureSeeded() {
        if (repository.count() == 0) repository.save(defaultEntity());
    }

    @Transactional(readOnly = true)
    public GeofenceSettings current() {
        HospitalCheckInConfig config = currentEntity();
        return new GeofenceSettings(config.getSiteId(), config.getLatitude(), config.getLongitude(),
                config.getRadiusMeters(), config.getMaxAccuracyMeters());
    }

    private HospitalCheckInConfig currentEntity() {
        return repository.findFirstByOrderByUpdatedAtDesc().orElseGet(this::defaultEntity);
    }

    private HospitalCheckInConfig defaultEntity() {
        HospitalCheckInConfig config = new HospitalCheckInConfig();
        config.setSiteId(defaultSiteId);
        config.setFacilityName(defaultFacilityName);
        config.setLatitude(defaultLatitude);
        config.setLongitude(defaultLongitude);
        config.setRadiusMeters(defaultRadiusMeters);
        config.setMaxAccuracyMeters(defaultMaxAccuracyMeters);
        return config;
    }

    private void validate(HospitalCheckInConfig config) {
        if (config.getLatitude() < -90 || config.getLatitude() > 90) {
            throw new IllegalArgumentException("Geofence latitude must be between -90 and 90");
        }
        if (config.getLongitude() < -180 || config.getLongitude() > 180) {
            throw new IllegalArgumentException("Geofence longitude must be between -180 and 180");
        }
    }

    private double requirePositive(double value, String field) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException("Geofence " + field + " must be positive");
        }
        return value;
    }

    public record GeofenceSettings(String siteId, double latitude, double longitude,
                                   double radiusMeters, double maxAccuracyMeters) {
    }
}
