package com.careflow.appointment.service;

import com.careflow.appointment.client.AppointmentConsultationClient;
import com.careflow.appointment.dto.request.CreateAppointmentRequest;
import com.careflow.appointment.model.Appointment;
import com.careflow.appointment.repository.AppointmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppointmentCacheTransactionTest {
    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void rollbackDoesNotExposeAnAppointmentThatWasNeverCommitted() {
        checkTransaction(false);
    }

    @Test
    void committedResultRemainsIdempotent() {
        checkTransaction(true);
    }

    private void checkTransaction(boolean commit) {
        var repository = mock(AppointmentRepository.class);
        var service = new AppointmentService(repository, mock(AppointmentEventService.class), AppointmentPricingFixture.priced(null), null, null,
                mock(AppointmentConsultationClient.class));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0); appointment.setId(UUID.randomUUID()); return appointment;
        });
        var request = CreateAppointmentRequest.builder().patientId(UUID.randomUUID()).patientName("Synthetic transaction test")
                .department("NOI_TONG_QUAT").appointmentDate(LocalDate.now().plusDays(14)).timeSlot("09:00-09:30").build();
        UUID owner = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();
        var first = service.createAppointment(request, owner, "trace", "transaction-key");
        if (commit) TransactionSynchronizationUtils.triggerAfterCommit();
        TransactionSynchronizationUtils.triggerAfterCompletion(commit ? TransactionSynchronization.STATUS_COMMITTED : TransactionSynchronization.STATUS_ROLLED_BACK);
        TransactionSynchronizationManager.clearSynchronization();
        var next = service.createAppointment(request, owner, "trace", "transaction-key");
        if (commit) assertThat(next.getId()).isEqualTo(first.getId());
        else assertThat(next.getId()).isNotEqualTo(first.getId());
        verify(repository, times(commit ? 1 : 2)).saveAndFlush(any());
    }
}
