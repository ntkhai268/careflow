package com.careflow.analytics;
import com.careflow.common.analytics.AnalyticsSnapshot.Source;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AnalyticsEventsTest {
    final SourceClient source=mock(SourceClient.class);final SnapshotStore store=mock(SnapshotStore.class);final EventIngestion ingestion=mock(EventIngestion.class);final ObjectMapper json=new ObjectMapper().findAndRegisterModules();final AnalyticsEvents events=new AnalyticsEvents(source,store,ingestion,json);
    Message message(Object value,String id)throws Exception{var p=new MessageProperties();if(id!=null)p.setMessageId(id);return new Message(json.writeValueAsBytes(value),p);}
    @Test void duplicateEventDoesNotReadSourceOrCreateAnotherProjection()throws Exception{UUID id=UUID.randomUUID(),event=UUID.randomUUID();when(store.processed(Source.APPOINTMENT,event)).thenReturn(true);events.consume(Source.APPOINTMENT,message(Map.of("eventId",event,"aggregateId",id,"producer","appointment-service","eventVersion",1),null));verifyNoInteractions(source,ingestion);}
    @Test void consultationOutboxMessageIdSupportsLegacyDtoBodyWithoutPersistingClinicalFields()throws Exception{var v=DemoSnapshots.forDay(LocalDate.of(2026,10,21)).get(5);UUID event=UUID.randomUUID();when(source.one(Source.CONSULTATION,v.id())).thenReturn(Optional.of(v));events.consume(Source.CONSULTATION,message(Map.of("id",v.id(),"diagnosis","SYNTHETIC_CLINICAL_FIELD_NOT_FOR_ANALYTICS"),event.toString()));verify(ingestion).commit(Source.CONSULTATION,event,v.id(),Optional.of(v));String exported=json.writeValueAsString(v);assertThat(exported).doesNotContain("patientId","patientName","diagnosis","doctorId","userId");}
    @Test void wrongProducerIsRejectedBeforeSourceCall()throws Exception{var message=message(Map.of("eventId",UUID.randomUUID(),"aggregateId",UUID.randomUUID(),"producer","untrusted-service","eventVersion",1),null);assertThatThrownBy(()->events.consume(Source.QUEUE,message)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(source,ingestion);}
    @Test void nonClinicalQueueMessagesDoNotBecomeVisitCounts()throws Exception{events.consume(Source.QUEUE,message(Map.of("eventId",UUID.randomUUID(),"aggregateId",UUID.randomUUID(),"producer","queue-service","eventVersion",1,"payload",Map.of("queueType","PHARMACY")),null));verifyNoInteractions(source,ingestion);}
}
