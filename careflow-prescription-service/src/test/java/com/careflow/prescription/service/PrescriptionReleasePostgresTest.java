package com.careflow.prescription.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.prescription.config.*;
import com.careflow.prescription.controller.PrescriptionReleaseController;
import com.careflow.prescription.mapper.PrescriptionMapper;
import com.careflow.prescription.model.*;
import com.careflow.prescription.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringJUnitConfig(PrescriptionReleasePostgresTest.Config.class)
@EnabledIfEnvironmentVariable(named="R3_DB_TESTS",matches="1")
class PrescriptionReleasePostgresTest {
    @Configuration @EnableTransactionManagement @EnableJpaRepositories(basePackages="com.careflow.prescription.repository")
    static class Config {
        @Bean DataSource dataSource(){var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:15432/careflow_r3_prescription_test",System.getenv("R3_DB_USER"),System.getenv("R3_DB_PASSWORD"));Flyway.configure().dataSource(ds).schemas("public").baselineOnMigrate(true).baselineVersion("0").load().migrate();return ds;}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){var em=new LocalContainerEntityManagerFactoryBean();em.setDataSource(ds);em.setPackagesToScan("com.careflow.prescription.model");em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());em.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return em;}
        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory em){return new JpaTransactionManager(em);}
        @Bean JdbcTemplate jdbcTemplate(DataSource ds){return new JdbcTemplate(ds);}
        @Bean ObjectMapper objectMapper(){return new ObjectMapper().findAndRegisterModules();}
        @Bean RabbitTemplate rabbitTemplate(){return mock(RabbitTemplate.class);}
        @Bean ConsultationClient consultations(){return mock(ConsultationClient.class);}
        @Bean AppointmentClient appointments(){return mock(AppointmentClient.class);}
        @Bean DirectoryClient directory(){return mock(DirectoryClient.class);}
        @Bean PrescriptionAiReleaseGuard aiGuard(JdbcTemplate jdbc){return new PrescriptionAiReleaseGuard(jdbc);}
        @Bean PrescriptionMapper mapper(){return new PrescriptionMapper();}
        @Bean PrescriptionEventOutbox outbox(JdbcTemplate jdbc,ObjectMapper json,RabbitTemplate rabbit,PlatformTransactionManager manager){return new PrescriptionEventOutbox(jdbc,json,rabbit,manager);}
        @Bean PrescriptionService prescriptions(PrescriptionRepository repo,DrugRepository drugs,PrescriptionMapper mapper,ConsultationClient consultations,DirectoryClient directory,ObjectMapper json,PrescriptionEventOutbox outbox,PrescriptionAiReleaseGuard guard){return new PrescriptionService(repo,drugs,mapper,consultations,directory,mock(QueueExecutionClient.class),mock(PatientIdentityClient.class),json,outbox,guard);}
        @Bean PrescriptionFollowUpService followUps(PrescriptionRepository repo,ConsultationClient consultations,AppointmentClient appointments,PrescriptionMapper mapper){return new PrescriptionFollowUpService(repo,consultations,appointments,mapper);}
    }
    @Autowired PrescriptionService prescriptions;
    @Autowired PrescriptionFollowUpService followUps;
    @Autowired PrescriptionEventOutbox outbox;
    @Autowired PrescriptionRepository repository;
    @Autowired ConsultationClient consultations;
    @Autowired AppointmentClient appointments;
    @Autowired DirectoryClient directory;
    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    final UUID patient=UUID.randomUUID(),doctor=UUID.randomUUID(),consultation=UUID.randomUUID();
    UUID id;LocalDate date;
    @BeforeEach void fixture(){
        jdbc.execute("TRUNCATE prescription_items,prescriptions,prescription_event_outbox CASCADE");reset(consultations,appointments,directory,rabbit);
        when(consultations.getConsultation(consultation)).thenReturn(new ConsultationClient.ConsultationContext(consultation,patient,doctor,"IN_PROGRESS"));when(directory.getActivePharmacyServicePointId()).thenReturn("PHARMACY-MAIN-01");
        date=LocalDate.now().plusDays(14);
        id=new TransactionTemplate(manager).execute(tx->{var p=Prescription.builder().consultationId(consultation).patientId(patient).doctorId(doctor).diagnosis("SYNTHETIC R3").followUpDate(date).followUpDepartment("CO_XUONG_KHOP").followUpTimeSlot("14:00-14:30").followUpStatus("PENDING").build();p.addItem(PrescriptionItem.builder().medicineCode("MED001").medicineName("Synthetic catalog item").quantity(1).build());return repository.saveAndFlush(p).getId();});
    }
    @Test void signatureCommitsBeforeFollowUpTimeoutAndRetryDoesNotIssueAgain(){
        when(appointments.schedule(patient,consultation,date,"CO_XUONG_KHOP","14:00-14:30",null,doctor)).thenThrow(new BusinessException(503,"timeout"));
        var controller=new PrescriptionReleaseController(prescriptions,followUps);
        var result=controller.release(id,doctor,"DOCTOR").getData();
        assertThat(result.getStatus()).isEqualTo(PrescriptionStatus.CONFIRMED);assertThat(result.getFollowUpStatus()).isEqualTo("FAILED");assertThat(result.getFollowUpAppointmentId()).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM prescriptions WHERE id=?",String.class,id)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_event_outbox",Integer.class)).isEqualTo(1);
        UUID booking=UUID.randomUUID();doReturn(booking).when(appointments).schedule(patient,consultation,date,"CO_XUONG_KHOP","14:00-14:30",null,doctor);
        assertThat(controller.retry(id,doctor,"DOCTOR").getData().getFollowUpAppointmentId()).isEqualTo(booking);
        assertThat(controller.release(id,doctor,"DOCTOR").getData().getFollowUpStatus()).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_event_outbox",Integer.class)).isEqualTo(1);
        verifyNoInteractions(rabbit);verify(appointments,times(2)).schedule(any(),any(),any(),any(),any(),any(),any());
    }
    @Test void concurrentSignatureReplaysHaveOneDurableEvent()throws Exception{
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->prescriptions.confirmPrescription(id,doctor,"DOCTOR"));var b=pool.submit(()->prescriptions.confirmPrescription(id,doctor,"DOCTOR"));assertThat(a.get(10,TimeUnit.SECONDS).getStatus()).isEqualTo(PrescriptionStatus.CONFIRMED);assertThat(b.get(10,TimeUnit.SECONDS).getStatus()).isEqualTo(PrescriptionStatus.CONFIRMED);}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_event_outbox",Integer.class)).isEqualTo(1);verifyNoInteractions(rabbit);
    }
    @Test void failedOutboxInsertRollsBackSignature(){
        jdbc.execute("ALTER TABLE prescription_event_outbox ADD CONSTRAINT r3_fail CHECK (event_type<>'PrescriptionIssued')");
        try{assertThatThrownBy(()->prescriptions.confirmPrescription(id,doctor,"DOCTOR")).isInstanceOf(RuntimeException.class);}finally{jdbc.execute("ALTER TABLE prescription_event_outbox DROP CONSTRAINT r3_fail");}
        assertThat(jdbc.queryForObject("SELECT status FROM prescriptions WHERE id=?",String.class,id)).isEqualTo("DRAFT");assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_event_outbox",Integer.class)).isZero();
    }
    @Test void brokerFailureRemainsPendingAndSuccessPublishesTheSameEvent(){
        prescriptions.confirmPrescription(id,doctor,"DOCTOR");UUID event=jdbc.queryForObject("SELECT event_id FROM prescription_event_outbox",UUID.class);
        doThrow(new org.springframework.amqp.AmqpException("Synthetic broker outage")).when(rabbit).convertAndSend(anyString(),anyString(),any(),any(org.springframework.amqp.core.MessagePostProcessor.class),any(CorrelationData.class));
        outbox.publishPending();assertThat(jdbc.queryForObject("SELECT published_at IS NULL FROM prescription_event_outbox",Boolean.class)).isTrue();assertThat(jdbc.queryForObject("SELECT attempts FROM prescription_event_outbox",Integer.class)).isEqualTo(1);
        reset(rabbit);jdbc.update("UPDATE prescription_event_outbox SET next_attempt_at=now()");
        doAnswer(call->{var data=call.getArgument(4,CorrelationData.class);assertThat(data.getId()).isEqualTo(event.toString());assertThat(call.getArgument(2,com.fasterxml.jackson.databind.JsonNode.class).get("eventId").asText()).isEqualTo(event.toString());data.getFuture().complete(new CorrelationData.Confirm(true,null));return null;}).when(rabbit).convertAndSend(anyString(),anyString(),any(),any(org.springframework.amqp.core.MessagePostProcessor.class),any(CorrelationData.class));
        outbox.publishPending();assertThat(jdbc.queryForObject("SELECT published_at IS NOT NULL FROM prescription_event_outbox",Boolean.class)).isTrue();
    }
    @Test void changedCurrentDoctorCannotRetryFollowUp(){
        prescriptions.confirmPrescription(id,doctor,"DOCTOR");when(consultations.getConsultation(consultation)).thenReturn(new ConsultationClient.ConsultationContext(consultation,patient,UUID.randomUUID(),"IN_PROGRESS"));
        assertThatThrownBy(()->followUps.attempt(id,doctor,"DOCTOR")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(403));verifyNoInteractions(appointments);
    }
}
