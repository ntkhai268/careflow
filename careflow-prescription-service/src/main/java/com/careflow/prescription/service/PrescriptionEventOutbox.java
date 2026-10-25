package com.careflow.prescription.service;

import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Transactional, at-least-once broker delivery. Consumers deduplicate the stable event ID. */
@Component
public class PrescriptionEventOutbox {
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final RabbitTemplate rabbit;private final TransactionTemplate transactions;
    public PrescriptionEventOutbox(JdbcTemplate jdbc,ObjectMapper json,RabbitTemplate rabbit,org.springframework.transaction.PlatformTransactionManager manager){this.jdbc=jdbc;this.json=json;this.rabbit=rabbit;this.transactions=new TransactionTemplate(manager);rabbit.setMandatory(true);}
    public void append(UUID aggregate,String type,String routing,Object event){
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Event must join its clinical transaction");
        try{
            UUID id=event instanceof com.careflow.common.event.EventEnvelope envelope?envelope.eventId():UUID.randomUUID();
            jdbc.update("INSERT INTO prescription_event_outbox(event_id,aggregate_id,event_type,exchange_name,routing_key,payload) VALUES (?,?,?,?,?,?::jsonb)",id,aggregate,type,"prescription.exchange",routing,json.writeValueAsString(event));
        }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new BusinessException(503,"Chưa thể lưu sự kiện đồng bộ. Chưa xác nhận thao tác.");}
    }
    @Scheduled(fixedDelayString="${clinical.outbox.delay-ms:2000}")
    public void publishPending(){
        transactions.executeWithoutResult(tx->{
            var rows=jdbc.queryForList("SELECT * FROM prescription_event_outbox WHERE published_at IS NULL AND next_attempt_at<=now() ORDER BY created_at,event_id LIMIT 20 FOR UPDATE SKIP LOCKED");
            for(var row:rows){
                UUID id=(UUID)row.get("event_id");
                try{
                    var correlation=new CorrelationData(id.toString());
                    rabbit.convertAndSend((String)row.get("exchange_name"),(String)row.get("routing_key"),json.readTree(row.get("payload").toString()),message->{message.getMessageProperties().setMessageId(id.toString());message.getMessageProperties().setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);return message;},correlation);
                    var confirmation=correlation.getFuture().get(5,TimeUnit.SECONDS);
                    if(!confirmation.isAck() || correlation.getReturned()!=null)throw new IllegalStateException("Broker did not route and confirm event");
                    jdbc.update("UPDATE prescription_event_outbox SET published_at=now(),attempts=attempts+1 WHERE event_id=?",id);
                }catch(Exception e){
                    if(e instanceof InterruptedException)Thread.currentThread().interrupt();
                    jdbc.update("UPDATE prescription_event_outbox SET attempts=attempts+1,next_attempt_at=now()+least(60,2+attempts*5)*interval '1 second' WHERE event_id=?",id);
                    org.slf4j.LoggerFactory.getLogger(PrescriptionEventOutbox.class).warn("event=CLINICAL_DELIVERY_RETRY eventId={}",id);
                }
            }
        });
    }
}
