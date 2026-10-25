package com.careflow.consultation.service;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@EnabledIfEnvironmentVariable(named="R3_DB_TESTS",matches="1")
class ConsultationOutboxPostgresTest {
    JdbcTemplate jdbc;TransactionTemplate tx;ConsultationEventOutbox outbox;UUID visit;
    @BeforeEach void setup(){
        var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:15432/careflow_r3_consultation_test",System.getenv("R3_DB_USER"),System.getenv("R3_DB_PASSWORD"));Flyway.configure().dataSource(ds).schemas("public").load().migrate();jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);outbox=new ConsultationEventOutbox(jdbc,new ObjectMapper().findAndRegisterModules(),mock(RabbitTemplate.class),manager);
        visit=UUID.randomUUID();jdbc.update("INSERT INTO consultations(id,created_at,updated_at,patient_id,doctor_id,status) VALUES (?,now(),now(),?,?,'IN_PROGRESS')",visit,UUID.randomUUID(),UUID.randomUUID());
    }
    @Test void completionAndEventCommitTogetherAndMigrationPreservesClinicalSchema(){
        tx.executeWithoutResult(status->{jdbc.update("UPDATE consultations SET status='COMPLETED' WHERE id=?",visit);outbox.append(visit,"ConsultationCompleted","consultation.completed",Map.of("id",visit.toString(),"status","COMPLETED"));});
        assertThat(jdbc.queryForObject("SELECT status FROM consultations WHERE id=?",String.class,visit)).isEqualTo("COMPLETED");assertThat(jdbc.queryForObject("SELECT count(*) FROM consultation_event_outbox WHERE aggregate_id=?",Integer.class,visit)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='consultations' AND column_name IN ('icd10_code','icd10_name','queue_entry_id')",Integer.class)).isEqualTo(3);
    }
    @Test void rollbackLeavesTheClinicalVisitOpenAndNoOutboxMessage(){
        assertThatThrownBy(()->tx.executeWithoutResult(status->{jdbc.update("UPDATE consultations SET status='COMPLETED' WHERE id=?",visit);outbox.append(visit,"ConsultationCompleted","consultation.completed",Map.of("id",visit.toString()));throw new IllegalStateException("Synthetic transaction failure");})).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM consultations WHERE id=?",String.class,visit)).isEqualTo("IN_PROGRESS");assertThat(jdbc.queryForObject("SELECT count(*) FROM consultation_event_outbox WHERE aggregate_id=?",Integer.class,visit)).isZero();
    }
}
