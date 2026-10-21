package com.careflow.consultation.service;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.consultation.client.ConsultationPrescriptionClient;
import com.careflow.consultation.model.Consultation;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ClinicalCompletionGuardTest {
    ConsultationPrescriptionClient prescriptions=mock(ConsultationPrescriptionClient.class);
    ClinicalCompletionGuard guard=new ClinicalCompletionGuard(prescriptions);
    Consultation visit;UUID doctor=UUID.randomUUID(),patient=UUID.randomUUID();
    @BeforeEach void setup(){visit=Consultation.builder().patientId(patient).doctorId(doctor).diagnosis("SYNTHETIC").build();visit.setId(UUID.randomUUID());}
    @Test void noDiagnosisDoesNotBecomeACompletedVisit(){visit.setDiagnosis(" ");assertThatThrownBy(()->guard.requireReady(visit)).isInstanceOf(BusinessException.class);verifyNoInteractions(prescriptions);}
    @Test void ownDraftBlocksCompletion(){when(prescriptions.byConsultation(visit.getId())).thenReturn(ApiResponse.success(List.of(new ConsultationPrescriptionClient.Prescription(UUID.randomUUID(),visit.getId(),patient,doctor,"DRAFT"))));assertThatThrownBy(()->guard.requireReady(visit)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(409));}
    @Test void inheritedUnreleasedDraftDoesNotRewriteAuthorshipOrBlockCurrentDoctorsSignedWork(){when(prescriptions.byConsultation(visit.getId())).thenReturn(ApiResponse.success(List.of(new ConsultationPrescriptionClient.Prescription(UUID.randomUUID(),visit.getId(),patient,UUID.randomUUID(),"DRAFT"),new ConsultationPrescriptionClient.Prescription(UUID.randomUUID(),visit.getId(),patient,doctor,"CONFIRMED"))));assertThatCode(()->guard.requireReady(visit)).doesNotThrowAnyException();}
    @Test void foreignPatientOrUnavailableProviderFailsClosed(){when(prescriptions.byConsultation(visit.getId())).thenReturn(ApiResponse.success(List.of(new ConsultationPrescriptionClient.Prescription(UUID.randomUUID(),visit.getId(),UUID.randomUUID(),doctor,"CONFIRMED"))));assertThatThrownBy(()->guard.requireReady(visit)).isInstanceOf(BusinessException.class);when(prescriptions.byConsultation(visit.getId())).thenReturn(null);assertThatThrownBy(()->guard.requireReady(visit)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(503));}
}
