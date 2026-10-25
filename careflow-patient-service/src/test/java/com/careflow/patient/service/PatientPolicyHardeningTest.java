package com.careflow.patient.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.patient.model.Patient;
import com.careflow.patient.repository.PatientRepository;
import com.careflow.patient.client.AssignmentClient;
import com.careflow.patient.dto.request.UpdatePatientRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatientPolicyHardeningTest {
    @ParameterizedTest @ValueSource(strings={"STAFF","ADMIN","LAB_TECHNICIAN","DOCTOR"})
    void nonOwnerCannotUseEmptyUpdateToReadOrModifyClinicalHistory(String role) {
        UUID owner=UUID.randomUUID(),actor=UUID.randomUUID(),id=UUID.randomUUID();
        Patient patient=Patient.builder().userId(owner).medicalHistory("private").build();
        var repository=mock(PatientRepository.class);when(repository.findById(id)).thenReturn(Optional.of(patient));
        var service=new PatientService(repository,mock(AssignmentClient.class));
        assertThatThrownBy(()->service.updatePatient(id,new UpdatePatientRequest(),actor,role))
                .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(403));
        verify(repository,never()).save(any());
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","STAFF","LAB_TECHNICIAN"})
    void privilegedAccountDoesNotBypassClinicalRead(String role) {
        var policy=new PatientAccessPolicy();var patient=Patient.builder().userId(UUID.randomUUID()).build();
        assertThatThrownBy(()->policy.requireClinicalRead(UUID.randomUUID(),role,patient,true)).isInstanceOf(BusinessException.class);
    }
}
