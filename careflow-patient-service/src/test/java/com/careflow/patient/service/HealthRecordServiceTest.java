package com.careflow.patient.service;

import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.patient.dto.request.CreateHealthRecordRequest;
import com.careflow.patient.dto.response.HealthRecordResponse;
import com.careflow.patient.model.AllergyStatus;
import com.careflow.patient.model.HealthRecord;
import com.careflow.patient.model.HealthRecordFile;
import com.careflow.patient.repository.HealthRecordRepository;
import com.careflow.patient.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthRecordServiceTest {

    @Mock
    private HealthRecordRepository healthRecordRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private HealthRecordService healthRecordService;

    private UUID patientId;
    private UUID recordId;
    private CreateHealthRecordRequest request;
    private HealthRecord savedRecord;

    @BeforeEach
    void setUp() {
        patientId = UUID.randomUUID();
        recordId = UUID.randomUUID();

        request = new CreateHealthRecordRequest();
        request.setTitle("Khám mắt");
        request.setRecordDate(LocalDate.of(2026, 7, 23));
        request.setFacilityName("BV Nhân Dân Gia Định");
        request.setNotes("Kết quả bình thường");
        request.setHeightCm(new BigDecimal("170"));
        request.setWeightKg(new BigDecimal("65"));
        request.setDrugAllergy(AllergyStatus.NO);

        savedRecord = HealthRecord.builder()
                .patientId(patientId)
                .title("Khám mắt")
                .recordDate(LocalDate.of(2026, 7, 23))
                .facilityName("BV Nhân Dân Gia Định")
                .notes("Kết quả bình thường")
                .heightCm(new BigDecimal("170"))
                .weightKg(new BigDecimal("65"))
                .bmi(new BigDecimal("22.49"))
                .drugAllergy(AllergyStatus.NO)
                .files(new ArrayList<>())
                .build();
        savedRecord.setId(recordId);
        savedRecord.setCreatedAt(Instant.now());
        savedRecord.setUpdatedAt(Instant.now());
    }

    @Test
    @DisplayName("Create health record without files")
    void create_withoutFiles_success() {
        when(patientRepository.existsById(patientId)).thenReturn(true);
        when(healthRecordRepository.save(any(HealthRecord.class))).thenReturn(savedRecord);

        HealthRecordResponse response = healthRecordService.create(patientId, request, null);

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Khám mắt");
        assertThat(response.facilityName()).isEqualTo("BV Nhân Dân Gia Định");
        assertThat(response.drugAllergy()).isEqualTo(AllergyStatus.NO);
        verify(healthRecordRepository).save(any(HealthRecord.class));
    }

    @Test
    @DisplayName("Create health record with files")
    void create_withFiles_success() {
        when(patientRepository.existsById(patientId)).thenReturn(true);
        when(fileStorageService.store(any(MultipartFile.class))).thenReturn("uuid-file.jpg");

        HealthRecordFile fileEntity = HealthRecordFile.builder()
                .fileName("xetnghiem.jpg")
                .storedPath("uuid-file.jpg")
                .fileSize(1024L)
                .contentType("image/jpeg")
                .build();
        fileEntity.setId(UUID.randomUUID());
        fileEntity.setCreatedAt(Instant.now());
        fileEntity.setUpdatedAt(Instant.now());
        savedRecord.getFiles().add(fileEntity);

        when(healthRecordRepository.save(any(HealthRecord.class))).thenReturn(savedRecord);

        MockMultipartFile mockFile = new MockMultipartFile(
                "files", "xetnghiem.jpg", "image/jpeg", "fake image data".getBytes());

        HealthRecordResponse response = healthRecordService.create(patientId, request, List.of(mockFile));

        assertThat(response).isNotNull();
        assertThat(response.files()).hasSize(1);
        verify(fileStorageService).store(any(MultipartFile.class));
    }

    @Test
    @DisplayName("Create health record - patient not found")
    void create_patientNotFound_throwsException() {
        when(patientRepository.existsById(patientId)).thenReturn(false);

        assertThatThrownBy(() -> healthRecordService.create(patientId, request, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Auto-calculate BMI when height and weight provided")
    void create_calculatessBmi() {
        when(patientRepository.existsById(patientId)).thenReturn(true);
        when(healthRecordRepository.save(any(HealthRecord.class))).thenAnswer(invocation -> {
            HealthRecord arg = invocation.getArgument(0);
            arg.setId(recordId);
            arg.setCreatedAt(Instant.now());
            arg.setUpdatedAt(Instant.now());
            if (arg.getFiles() == null) arg.setFiles(new ArrayList<>());
            return arg;
        });

        HealthRecordResponse response = healthRecordService.create(patientId, request, null);

        assertThat(response).isNotNull();
        // BMI = 65 / (1.70 * 1.70) = 22.49
        assertThat(response.bmi()).isNotNull();
        assertThat(response.bmi().doubleValue()).isBetween(22.0, 23.0);
    }

    @Test
    @DisplayName("Get all health records for a patient")
    void getAll_success() {
        when(patientRepository.existsById(patientId)).thenReturn(true);
        when(healthRecordRepository.findByPatientIdOrderByRecordDateDesc(patientId))
                .thenReturn(List.of(savedRecord));

        List<HealthRecordResponse> responses = healthRecordService.getAll(patientId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).title()).isEqualTo("Khám mắt");
    }

    @Test
    @DisplayName("Get all - patient not found")
    void getAll_patientNotFound_throwsException() {
        when(patientRepository.existsById(patientId)).thenReturn(false);

        assertThatThrownBy(() -> healthRecordService.getAll(patientId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Get health record by ID")
    void getById_success() {
        when(healthRecordRepository.findByIdAndPatientId(recordId, patientId))
                .thenReturn(Optional.of(savedRecord));

        HealthRecordResponse response = healthRecordService.getById(patientId, recordId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(recordId);
    }

    @Test
    @DisplayName("Get by ID - not found")
    void getById_notFound_throwsException() {
        when(healthRecordRepository.findByIdAndPatientId(recordId, patientId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> healthRecordService.getById(patientId, recordId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Delete health record + files from disk")
    void delete_success() {
        HealthRecordFile fileEntity = HealthRecordFile.builder()
                .fileName("xetnghiem.jpg")
                .storedPath("uuid-file.jpg")
                .build();
        fileEntity.setId(UUID.randomUUID());
        fileEntity.setCreatedAt(Instant.now());
        fileEntity.setUpdatedAt(Instant.now());
        savedRecord.getFiles().add(fileEntity);

        when(healthRecordRepository.findByIdAndPatientId(recordId, patientId))
                .thenReturn(Optional.of(savedRecord));

        healthRecordService.delete(patientId, recordId);

        verify(fileStorageService).delete("uuid-file.jpg");
        verify(healthRecordRepository).delete(savedRecord);
    }

    @Test
    @DisplayName("Delete - not found")
    void delete_notFound_throwsException() {
        when(healthRecordRepository.findByIdAndPatientId(recordId, patientId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> healthRecordService.delete(patientId, recordId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
