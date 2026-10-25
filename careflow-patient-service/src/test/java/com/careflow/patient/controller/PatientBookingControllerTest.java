package com.careflow.patient.controller;

import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.GlobalExceptionHandler;
import com.careflow.patient.dto.response.PatientResponse;
import com.careflow.patient.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PatientBookingControllerTest {
    @Mock PatientService patients;
    private MockMvc mvc;
    private final UUID actor = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PatientBookingController(patients))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void bookingReturnsOnlyMinimalDemographics() throws Exception {
        when(patients.getPatientById(patient, actor, "PATIENT")).thenReturn(PatientResponse.builder()
                .id(patient).userId(actor).fullName("Synthetic name").phone("private contact")
                .medicalHistory("private clinical field").allergyNotes("private allergy field").build());
        mvc.perform(get("/api/patients/" + patient + "/booking-context")
                .header("X-User-Id", actor).header("X-User-Role", "PATIENT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(patient.toString()))
                .andExpect(jsonPath("$.data.userId").value(actor.toString()))
                .andExpect(jsonPath("$.data.fullName").value("Synthetic name"))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data.medicalHistory").doesNotExist());
    }

    @Test
    void doctorCannotUseAdministrativeBookingContext() throws Exception {
        mvc.perform(get("/api/patients/" + patient + "/booking-context")
                .header("X-User-Id", actor).header("X-User-Role", "DOCTOR"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(patients);
    }

    @Test
    void otherOwnerDenialIsPreserved() throws Exception {
        when(patients.getPatientById(patient, actor, "PATIENT")).thenThrow(new BusinessException(403, "Not owner"));
        mvc.perform(get("/api/patients/" + patient + "/booking-context")
                .header("X-User-Id", actor).header("X-User-Role", "PATIENT"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.data").doesNotExist());
    }
}
