package com.careflow.appointment.service;

import com.careflow.appointment.client.PatientClient;
import com.careflow.appointment.dto.request.CreateAppointmentRequest;
import com.careflow.appointment.model.Appointment;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentBookingOwnershipTest {
    @Mock AppointmentRepository repository;
    @Mock AppointmentEventService events;
    @Mock PatientClient patients;
    private AppointmentService service;
    private final UUID actor = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AppointmentService(repository, events, AppointmentPricingFixture.priced(null), patients, null,
                mock(com.careflow.appointment.client.AppointmentConsultationClient.class));
    }

    private CreateAppointmentRequest request(UUID patientId) {
        return CreateAppointmentRequest.builder().patientId(patientId).patientName("Untrusted name")
                .department("NOI_TONG_QUAT").appointmentDate(LocalDate.now().plusDays(20))
                .timeSlot("09:00-09:30").reason("Synthetic test").build();
    }

    private void successfulSave() {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            Appointment saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    @Test
    void ownerAndNameComeFromVerifiedPatient() {
        when(patients.getBookingContext(patient)).thenReturn(ApiResponse.success(new PatientClient.BookingContext(patient, actor, "Verified name")));
        successfulSave();
        var result = service.bookAppointment(request(patient), actor, "PATIENT", "trace", "key");
        assertThat(result.getOwnerUserId()).isEqualTo(actor);
        assertThat(result.getPatientName()).isEqualTo("Verified name");
        verify(events).confirmed(any(), eq("trace"));
    }

    @Test
    void adminBookingKeepsPatientOwner() {
        UUID owner = UUID.randomUUID();
        when(patients.getBookingContext(patient)).thenReturn(ApiResponse.success(new PatientClient.BookingContext(patient, owner, "Verified name")));
        successfulSave();
        assertThat(service.bookAppointment(request(patient), actor, "ADMIN", "trace", "key").getOwnerUserId()).isEqualTo(owner);
    }

    @Test
    void differentOwnerIsDeniedEvenWithSuccessfulProviderEnvelope() {
        when(patients.getBookingContext(patient)).thenReturn(ApiResponse.success(new PatientClient.BookingContext(patient, UUID.randomUUID(), "Name")));
        denied(403, () -> service.bookAppointment(request(patient), actor, "PATIENT", "trace", "key"));
        verifyNoInteractions(repository, events);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOCTOR", "STAFF", "LAB_TECHNICIAN"})
    void nonBookingRolesCannotCallProvider(String role) {
        denied(403, () -> service.bookAppointment(request(patient), actor, role, "trace", "key"));
        verifyNoInteractions(patients, repository, events);
    }

    @ParameterizedTest
    @CsvSource({"401,401", "403,403", "404,404", "500,503", "503,503"})
    void providerErrorsCannotCreateAppointments(int remote, int expected) {
        Request req = Request.create(Request.HttpMethod.GET, "http://patient.test", Map.of(), null, StandardCharsets.UTF_8, null);
        var failure = FeignException.errorStatus("getBookingContext", Response.builder().request(req).status(remote).headers(Map.of()).build());
        when(patients.getBookingContext(patient)).thenThrow(failure);
        denied(expected, () -> service.bookAppointment(request(patient), actor, "PATIENT", "trace", "key"));
        verifyNoInteractions(repository, events);
    }

    @Test
    void incompleteProfileCannotCreateAppointments() {
        when(patients.getBookingContext(patient)).thenReturn(ApiResponse.success(new PatientClient.BookingContext(patient, null, "Name")));
        denied(503, () -> service.bookAppointment(request(patient), actor, "PATIENT", "trace", "key"));
        verifyNoInteractions(repository, events);
    }

    @Test
    void wrongProfileCannotBeUsed() {
        when(patients.getBookingContext(patient)).thenReturn(ApiResponse.success(new PatientClient.BookingContext(UUID.randomUUID(), actor, "Name")));
        denied(503, () -> service.bookAppointment(request(patient), actor, "PATIENT", "trace", "key"));
        verifyNoInteractions(repository, events);
    }

    @Test
    void sameIdempotencyKeyDoesNotShareResponsesAcrossOwners() {
        successfulSave();
        var first = service.createAppointment(request(patient), actor, "trace", "shared-key");
        var second = service.createAppointment(request(UUID.randomUUID()), UUID.randomUUID(), "trace", "shared-key");
        assertThat(second.getId()).isNotEqualTo(first.getId());
        verify(repository, times(2)).saveAndFlush(any());
    }

    @Test
    void sameOwnerCannotReuseKeyForDifferentProfile() {
        successfulSave();
        service.createAppointment(request(patient), actor, "trace", "shared-key");
        denied(409, () -> service.createAppointment(request(UUID.randomUUID()), actor, "trace", "shared-key"));
        verify(repository).saveAndFlush(any());
    }

    @Test
    void cachedAppointmentDoesNotSkipCurrentOwnershipCheck() {
        successfulSave();
        when(patients.getBookingContext(patient)).thenReturn(ApiResponse.success(new PatientClient.BookingContext(patient, actor, "Name")))
                .thenThrow(FeignException.errorStatus("getBookingContext", Response.builder().request(
                        Request.create(Request.HttpMethod.GET, "http://patient.test", Map.of(), null, StandardCharsets.UTF_8, null))
                        .status(403).headers(Map.of()).build()));
        service.bookAppointment(request(patient), actor, "PATIENT", "trace", "shared-key");
        denied(403, () -> service.bookAppointment(request(patient), actor, "PATIENT", "trace", "shared-key"));
        verify(repository).saveAndFlush(any());
    }

    private void denied(int status, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getStatus()).isEqualTo(status));
    }
}
