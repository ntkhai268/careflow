package com.careflow.consultation.service;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.consultation.client.*;
import com.careflow.consultation.client.dto.AppointmentResponse;
import com.careflow.consultation.dto.response.ConsultationResponse;
import com.careflow.consultation.model.ConsultationStatus;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ClinicalFinalizationTest {
    final UUID id=UUID.randomUUID(),patient=UUID.randomUUID(),doctor=UUID.randomUUID(),entry=UUID.randomUUID(),appointment=UUID.randomUUID();
    ConsultationService consultations=mock(ConsultationService.class);ConsultationQueueClient queues=mock(ConsultationQueueClient.class);AppointmentClient appointments=mock(AppointmentClient.class);
    ClinicalFinalizationService service=new ClinicalFinalizationService(consultations,queues,appointments);
    ConsultationResponse closed;
    @BeforeEach void setup(){closed=ConsultationResponse.builder().id(id).patientId(patient).doctorId(doctor).queueEntryId(entry).status(ConsultationStatus.COMPLETED).build();when(consultations.completeConsultation(id,doctor,"DOCTOR")).thenReturn(closed);}
    @Test void closedClinicalStateIsReturnedWithExplicitQueueFailureThenRetried(){
        when(queues.completionEntries(id,patient,entry)).thenThrow(new BusinessException(503,"Synthetic outage"));
        var first=service.finalizeVisit(id,doctor,"DOCTOR");assertThat(first.consultation().getStatus()).isEqualTo(ConsultationStatus.COMPLETED);assertThat(first.complete()).isFalse();assertThat(first.pendingSteps()).containsExactly("QUEUE_COMPLETION");
        doReturn(ApiResponse.success(List.of(new ConsultationQueueClient.CompletionEntry(entry,patient,"INITIAL","COMPLETED")))).when(queues).completionEntries(id,patient,entry);
        assertThat(service.finalizeVisit(id,doctor,"DOCTOR").complete()).isTrue();verify(queues,never()).complete(any());
    }
    @Test void resultReviewUsesItsPersistedEntryAndEveryIncompleteStepIsReported(){
        UUID review=UUID.randomUUID();closed.setAppointmentId(appointment);
        when(queues.completionEntries(id,patient,entry)).thenReturn(ApiResponse.success(List.of(new ConsultationQueueClient.CompletionEntry(entry,patient,"INITIAL","COMPLETED"),new ConsultationQueueClient.CompletionEntry(review,patient,"RESULT_REVIEW","IN_PROGRESS"))));
        when(queues.complete(review)).thenReturn(ApiResponse.success(new ConsultationQueueClient.CompletionResult(review,patient,"COMPLETED")));
        doThrow(new BusinessException(503,"Synthetic outage")).when(appointments).completeClinical(any());
        var result=service.finalizeVisit(id,doctor,"DOCTOR");assertThat(result.queueCompleted()).isTrue();assertThat(result.appointmentCompleted()).isFalse();assertThat(result.pendingSteps()).containsExactly("APPOINTMENT_COMPLETION");verify(queues).complete(review);verify(queues,never()).complete(entry);
        doReturn(ApiResponse.success(AppointmentResponse.builder().id(appointment).patientId(patient).status("COMPLETED").build())).when(appointments).completeClinical(any());
        assertThat(service.finalizeVisit(id,doctor,"DOCTOR").complete()).isTrue();
    }
    @Test void mismatchedQueueIdentityNeverCompletesTheReturnedEntry(){
        when(queues.completionEntries(id,patient,entry)).thenReturn(ApiResponse.success(List.of(new ConsultationQueueClient.CompletionEntry(UUID.randomUUID(),UUID.randomUUID(),"INITIAL","IN_PROGRESS"))));
        assertThat(service.finalizeVisit(id,doctor,"DOCTOR").complete()).isFalse();verify(queues,never()).complete(any());
    }
    @Test void rejectedClinicalAuthorityNeverReachesDownstreamSteps(){
        when(consultations.completeConsultation(id,doctor,"DOCTOR")).thenThrow(new BusinessException(403,"Assignment changed"));
        assertThatThrownBy(()->service.finalizeVisit(id,doctor,"DOCTOR")).isInstanceOf(BusinessException.class);verifyNoInteractions(queues,appointments);
    }
}
