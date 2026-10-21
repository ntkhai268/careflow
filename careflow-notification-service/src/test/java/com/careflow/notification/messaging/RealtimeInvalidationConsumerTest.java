package com.careflow.notification.messaging;
import com.careflow.common.event.EventEnvelope;
import com.careflow.common.security.PatientOwnershipClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
class RealtimeInvalidationConsumerTest {
    @Test void hintUsesCurrentOwnerAndContainsNoClinicalPayload(){var owners=mock(PatientOwnershipClient.class);var messaging=mock(SimpMessagingTemplate.class);UUID patient=UUID.randomUUID(),owner=UUID.randomUUID(),id=UUID.randomUUID();when(owners.owner(patient)).thenReturn(owner);var payload=new ObjectMapper().valueToTree(Map.of("patientId",patient,"diagnosis","SYNTHETIC PRIVATE"));new RealtimeInvalidationConsumer(owners,messaging).consume(new EventEnvelope(id,"ConsultationCompleted",1,UUID.randomUUID(),1,Instant.now(),"consultation-service",id.toString(),payload));verify(messaging).convertAndSendToUser(owner.toString(),"/queue/notifications",Map.of("id",id,"invalidate",true));}
    @Test void offlinePatientDoesNotInventARecipient(){var owners=mock(PatientOwnershipClient.class);var messaging=mock(SimpMessagingTemplate.class);var payload=new ObjectMapper().valueToTree(Map.of("patientId",UUID.randomUUID()));UUID id=UUID.randomUUID();new RealtimeInvalidationConsumer(owners,messaging).consume(new EventEnvelope(id,"QueueEntryCompleted",1,id,1,Instant.now(),"queue-service",id.toString(),payload));verifyNoInteractions(messaging);}
}
