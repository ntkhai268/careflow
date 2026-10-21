package com.careflow.analytics;
import com.careflow.common.analytics.AnalyticsSnapshot.Source;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.core.Message;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
@ConditionalOnProperty(name="analytics.demo-enabled",havingValue="false",matchIfMissing=true)
public class AnalyticsEvents {
    private final SourceClient sources;private final SnapshotStore store;private final EventIngestion ingestion;private final ObjectMapper json;
    public AnalyticsEvents(SourceClient sources,SnapshotStore store,EventIngestion ingestion,ObjectMapper json){this.sources=sources;this.store=store;this.ingestion=ingestion;this.json=json;}
    @RabbitListener(queues="analytics.appointment.events") public void appointment(Message message)throws Exception{consume(Source.APPOINTMENT,message);}
    @RabbitListener(queues="analytics.queue.events") public void queue(Message message)throws Exception{consume(Source.QUEUE,message);}
    @RabbitListener(queues="analytics.consultation.events") public void consultation(Message message)throws Exception{consume(Source.CONSULTATION,message);}
    public void consume(Source source,Message message)throws Exception{
        JsonNode body=json.readTree(message.getBody());UUID id,event;
        if(body.hasNonNull("eventId")){
            String expected=source.name().toLowerCase(Locale.ROOT)+"-service";
            if(!expected.equals(body.path("producer").asText())||body.path("eventVersion").asInt()!=1)throw new IllegalArgumentException("Unexpected source event");
            id=UUID.fromString(body.path("aggregateId").asText());event=UUID.fromString(body.path("eventId").asText());
            if(source==Source.QUEUE&&body.path("payload").hasNonNull("queueType")&&!"CONSULTATION".equals(body.path("payload").path("queueType").asText()))return;
        }else{
            if(source!=Source.CONSULTATION)throw new IllegalArgumentException("Missing event envelope");
            id=UUID.fromString(body.path("id").asText());event=UUID.fromString(message.getMessageProperties().getMessageId());
        }
        if(store.processed(source,event))return;
        ingestion.commit(source,event,id,sources.one(source,id));
    }
}
