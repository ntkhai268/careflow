package com.careflow.lab.service;
import com.careflow.common.event.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.util.*;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@EnabledIfEnvironmentVariable(named="R3_DB_TESTS",matches="1")
class LabOutboxPostgresTest {
    JdbcTemplate jdbc;TransactionTemplate tx;LabEventOutbox outbox;ObjectMapper json=new ObjectMapper().findAndRegisterModules();UUID order;
    @BeforeEach void setup(){var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:15432/careflow_r3_lab_test",System.getenv("R3_DB_USER"),System.getenv("R3_DB_PASSWORD"));Flyway.configure().dataSource(ds).schemas("public").load().migrate();jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);outbox=new LabEventOutbox(jdbc,json,mock(RabbitTemplate.class),manager);order=UUID.randomUUID();jdbc.update("INSERT INTO lab_orders(id,patient_id,consultation_id,ordered_by_doctor_id,status,payment_status,created_at,updated_at) VALUES (?,?,?,?,'IN_PROGRESS','NOT_REQUIRED',now(),now())",order,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());}
    EventEnvelope event(){return new EventEnvelope(UUID.randomUUID(),"AllRequiredResultsAvailable",1,order,1,Instant.now(),"lab-service","synthetic",json.valueToTree(Map.of("orderId",order,"sourceQueueEntryId",UUID.randomUUID())));}
    @Test void clinicalRollbackLeavesNoResultEvent(){assertThatThrownBy(()->tx.executeWithoutResult(status->{jdbc.update("UPDATE lab_orders SET status='RESULT_AVAILABLE' WHERE id=?",order);var event=event();outbox.append(order,event.eventType(),"lab.result.available",event);throw new IllegalStateException("Synthetic rollback");})).isInstanceOf(IllegalStateException.class);assertThat(jdbc.queryForObject("SELECT status FROM lab_orders WHERE id=?",String.class,order)).isEqualTo("IN_PROGRESS");assertThat(jdbc.queryForObject("SELECT count(*) FROM lab_event_outbox WHERE aggregate_id=?",Integer.class,order)).isZero();}
    @Test void explicitResynchronizationRetainsOriginalStableEventAndPayload(){var first=event();tx.executeWithoutResult(status->outbox.append(order,first.eventType(),"lab.result.available",first));String payload=jdbc.queryForObject("SELECT payload::text FROM lab_event_outbox WHERE aggregate_id=?",String.class,order);jdbc.update("UPDATE lab_event_outbox SET published_at=now() WHERE aggregate_id=?",order);tx.executeWithoutResult(status->{var repeated=event();outbox.append(order,repeated.eventType(),"lab.result.available",repeated);});assertThat(jdbc.queryForObject("SELECT event_id FROM lab_event_outbox WHERE aggregate_id=?",UUID.class,order)).isEqualTo(first.eventId());assertThat(jdbc.queryForObject("SELECT payload::text FROM lab_event_outbox WHERE aggregate_id=?",String.class,order)).isEqualTo(payload);assertThat(jdbc.queryForObject("SELECT published_at IS NULL FROM lab_event_outbox WHERE aggregate_id=?",Boolean.class,order)).isTrue();}
}
