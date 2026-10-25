package com.careflow.prescription.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.prescription.model.*;
import com.careflow.prescription.repository.PrescriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrescriptionAdministrativeBoundaryTest {
    @Mock PrescriptionRepository repository;
    @InjectMocks PrescriptionService service;
    @Test void administratorCannotCancelClinicalPrescription() {
        assertThatThrownBy(()->service.cancelPrescription(UUID.randomUUID(),"reason",UUID.randomUUID(),"ADMIN"))
                .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(403));
        verifyNoInteractions(repository);
    }
    @Test void administratorCannotDispenseClinicalPrescription() {
        assertThatThrownBy(()->service.dispensePrescription(UUID.randomUUID(),UUID.randomUUID(),"ADMIN","correlation"))
                .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(403));
        verifyNoInteractions(repository);
    }
    @Test void unrelatedStaffCannotUseIdempotentDispenseToReadSomeoneElsesPrescription() {
        UUID id=UUID.randomUUID();Prescription prescription=new Prescription();
        prescription.setStatus(PrescriptionStatus.DISPENSED);prescription.setDispensedByUserId(UUID.randomUUID());
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(prescription));
        assertThatThrownBy(()->service.dispensePrescription(id,UUID.randomUUID(),"STAFF","correlation"))
                .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(403));
        verify(repository,never()).save(any());
    }
}
