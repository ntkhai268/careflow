package com.careflow.patient.config;

import com.careflow.patient.repository.PatientAllergyRepository;
import com.careflow.patient.repository.PatientRepository;
import com.careflow.patient.model.Patient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataInitializerSafetyTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientAllergyRepository patientAllergyRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedNeverDeletesPatients() {
        when(patientRepository.existsById(any())).thenReturn(false);
        when(patientRepository.existsByUserId(any())).thenReturn(false);
        when(patientAllergyRepository.findByPatientId(any())).thenReturn(List.of());

        new DataInitializer(patientRepository, patientAllergyRepository, jdbcTemplate).run();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).update(sqlCaptor.capture(), any(Object[].class));
        assertThat(sqlCaptor.getAllValues())
                .noneMatch(sql -> sql.trim().toUpperCase().startsWith("DELETE FROM PATIENTS"));
    }

    @Test
    void existingPatientWithSameUserIdKeepsItsStableDatabaseId() {
        UUID seededUserId = UUID.fromString("00000001-0000-0000-0000-000000000001");
        UUID existingId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Patient existing = new Patient();
        existing.setId(existingId);
        existing.setUserId(seededUserId);

        when(patientRepository.existsById(any())).thenReturn(false);
        when(patientRepository.existsByUserId(any())).thenAnswer(invocation ->
                seededUserId.equals(invocation.getArgument(0)));
        when(patientRepository.findFirstByUserIdOrderByCreatedAtAsc(any())).thenAnswer(invocation ->
                seededUserId.equals(invocation.getArgument(0))
                        ? Optional.of(existing)
                        : Optional.empty());
        when(patientAllergyRepository.findByPatientId(any())).thenReturn(List.of());

        new DataInitializer(patientRepository, patientAllergyRepository, jdbcTemplate).run();

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).update(anyString(), argsCaptor.capture());
        assertThat(argsCaptor.getAllValues())
                .anyMatch(args -> args.length == 3 && existingId.equals(args[2]));
    }
}
