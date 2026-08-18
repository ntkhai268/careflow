package com.careflow.queue.service;

import com.careflow.queue.domain.HospitalCheckInConfig;
import com.careflow.queue.dto.HospitalCheckInConfigRequest;
import com.careflow.queue.repository.HospitalCheckInConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HospitalCheckInConfigServiceTest {
    @Mock HospitalCheckInConfigRepository repository;

    @Test
    void returnsConfiguredValuesAndPersistsAdminUpdates() {
        HospitalCheckInConfig existing = new HospitalCheckInConfig();
        existing.setSiteId("HOSPITAL-MAIN");
        existing.setFacilityName("Bệnh viện CareFlow");
        existing.setLatitude(10.7769);
        existing.setLongitude(106.7009);
        existing.setRadiusMeters(150);
        existing.setMaxAccuracyMeters(50);
        when(repository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(existing));
        when(repository.save(any(HospitalCheckInConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HospitalCheckInConfigService service = service();
        HospitalCheckInConfigService.GeofenceSettings current = service.current();

        assertThat(current.radiusMeters()).isEqualTo(150);
        assertThat(current.maxAccuracyMeters()).isEqualTo(50);

        service.save(new HospitalCheckInConfigRequest(
                "HOSPITAL-MAIN", "Bệnh viện CareFlow", 10.777, 106.701, 250, 30));

        assertThat(existing.getLatitude()).isEqualTo(10.777);
        assertThat(existing.getLongitude()).isEqualTo(106.701);
        assertThat(existing.getRadiusMeters()).isEqualTo(250);
        assertThat(existing.getMaxAccuracyMeters()).isEqualTo(30);
        verify(repository).save(existing);
    }

    @Test
    void seedsDefaultsWhenNoConfigExists() {
        when(repository.count()).thenReturn(0L);
        when(repository.save(any(HospitalCheckInConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().ensureSeeded();

        verify(repository).save(argThat(config ->
                config.getSiteId().equals("HOSPITAL-MAIN")
                        && config.getRadiusMeters() == 150
                        && config.getMaxAccuracyMeters() == 50));
    }

    private HospitalCheckInConfigService service() {
        return new HospitalCheckInConfigService(
                repository, "HOSPITAL-MAIN", "Bệnh viện CareFlow", 10.7769, 106.7009, 150, 50);
    }
}
