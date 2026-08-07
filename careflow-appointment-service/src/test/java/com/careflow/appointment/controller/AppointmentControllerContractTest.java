package com.careflow.appointment.controller;

import com.careflow.appointment.service.AppointmentService;
import com.careflow.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AppointmentControllerContractTest {

    @Mock
    private AppointmentService appointmentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AppointmentController(appointmentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createAppointmentRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/appointments")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "PATIENT")
                        .contentType("application/json")
                        .content("{"
                                + "\"patientId\":\"11111111-1111-1111-1111-111111111111\","
                                + "\"patientName\":\"Test Patient\","
                                + "\"department\":\"NOI_TONG_QUAT\","
                                + "\"appointmentDate\":\"2099-08-18\","
                                + "\"timeSlot\":\"10:00-10:30\""
                                + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clinicalContextReturnsDoctorAssignmentForDoctor() throws Exception {
        mockMvc.perform(get("/api/appointments/clinical-context/me")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "DOCTOR"))
                .andExpect(status().isOk());
    }
}
