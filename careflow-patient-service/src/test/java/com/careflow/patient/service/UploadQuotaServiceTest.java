package com.careflow.patient.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.patient.model.Patient;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadQuotaServiceTest {
    @Test void quotaSerializesOnPatientAndRejectsStoredPlusIncomingBytes() {
        UUID id=UUID.randomUUID();var manager=mock(EntityManager.class);
        @SuppressWarnings("unchecked") TypedQuery<Object[]> query=mock(TypedQuery.class);
        when(manager.find(Patient.class,id,LockModeType.PESSIMISTIC_WRITE)).thenReturn(new Patient());
        when(manager.createQuery(anyString(),eq(Object[].class))).thenReturn(query);when(query.setParameter("patient",id)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(new Object[]{99L,199L*1024*1024});
        var service=new UploadQuotaService();ReflectionTestUtils.setField(service,"entityManager",manager);
        assertThatCode(()->service.requireCapacity(id,1024*1024,1)).doesNotThrowAnyException();
        assertThatThrownBy(()->service.requireCapacity(id,1024*1024+1,1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.requireCapacity(id,1,2)).isInstanceOf(BusinessException.class);
        verify(manager,times(3)).find(Patient.class,id,LockModeType.PESSIMISTIC_WRITE);
    }
}
