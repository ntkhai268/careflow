package com.careflow.queue.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.queue.domain.*;
import com.careflow.queue.repository.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResultReviewSourceTest {
    final QueueEntryRepository entries=mock(QueueEntryRepository.class);
    final QueueManagementService service=new QueueManagementService(mock(QueueConfigRepository.class),mock(QueueNumberSequenceRepository.class),mock(ServicePointSequenceRepository.class),entries,mock(IdempotencyRecordRepository.class),mock(QueueEventService.class),mock(QrTokenService.class),"Asia/Ho_Chi_Minh",mock(com.careflow.queue.client.DirectoryClient.class));
    final UUID patient=UUID.randomUUID(),visit=UUID.randomUUID(),source=UUID.randomUUID();
    QueueEntry initial(){var e=new QueueEntry();e.setId(source);e.setPatientId(patient);e.setQueueConfigId(UUID.randomUUID());e.setConsultationId(visit);e.setQueueType(QueueType.CONSULTATION);e.setConsultationPhase(ConsultationPhase.INITIAL);e.setQueueNumber("SYNTHETIC-001");e.setPatientNameSnapshot("SYNTHETIC OWNER");e.setServiceCode("GENERAL_CONSULTATION");return e;}
    @Test void missingSourceNeverGuessesLatestVisitForPatient(){assertThatThrownBy(()->service.createResultReviewEntry(visit,patient,null,Instant.now())).isInstanceOf(BusinessException.class);verify(entries).findByConsultationIdAndQueueTypeAndConsultationPhase(visit,QueueType.CONSULTATION,ConsultationPhase.RESULT_REVIEW);verify(entries).findByConsultationIdAndQueueTypeAndConsultationPhase(visit,QueueType.CONSULTATION,ConsultationPhase.INITIAL);verifyNoMoreInteractions(entries);}
    @Test void exactSourceRetainsPatientAndServiceSnapshots(){var e=initial();when(entries.findById(source)).thenReturn(Optional.of(e));var review=service.createResultReviewEntry(visit,patient,source,Instant.now());assertThat(review.getConsultationId()).isEqualTo(visit);assertThat(review.getQueueConfigId()).isEqualTo(e.getQueueConfigId());assertThat(review.getPatientNameSnapshot()).isEqualTo("SYNTHETIC OWNER");assertThat(review.getServiceCode()).isEqualTo("GENERAL_CONSULTATION");assertThat(review.getConsultationPhase()).isEqualTo(ConsultationPhase.RESULT_REVIEW);}
    @Test void anotherPatientOrAnotherVisitCannotSupplySource(){var e=initial();when(entries.findById(source)).thenReturn(Optional.of(e));e.setPatientId(UUID.randomUUID());assertThatThrownBy(()->service.createResultReviewEntry(visit,patient,source,Instant.now())).isInstanceOf(BusinessException.class);e.setPatientId(patient);e.setConsultationId(UUID.randomUUID());assertThatThrownBy(()->service.createResultReviewEntry(visit,patient,source,Instant.now())).isInstanceOf(BusinessException.class);verify(entries,never()).save(any());}
}
