package com.careflow.queue.service;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.security.PatientOwnershipClient;
import com.careflow.queue.client.*;
import com.careflow.queue.client.dto.DoctorAssignmentResponse;
import com.careflow.queue.domain.*;
import com.careflow.queue.dto.*;
import com.careflow.queue.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@EnabledIfEnvironmentVariable(named="R2_DB_TESTS",matches="1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoomQueuePostgresTest {
    static final UUID DEPT=UUID.randomUUID(),OWNER=UUID.randomUUID(),ACTOR=UUID.randomUUID();
    AnnotationConfigApplicationContext context;QueueManagementService queues;QueueRoomConfigurationService policies;JdbcTemplate jdbc;TransactionTemplate tx;
    @Configuration @EnableTransactionManagement @EnableJpaRepositories(basePackageClasses=QueueConfigRepository.class)
    static class Config {
        @Bean DataSource dataSource(){var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:15432/careflow_r2_queue_test",System.getenv("R2_DB_USER"),System.getenv("R2_DB_PASSWORD"));Flyway.configure().dataSource(ds).schemas("queue").defaultSchema("queue").locations("classpath:db/migration").load().migrate();return ds;}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("com.careflow.queue.domain");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return f;}
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory f){return new JpaTransactionManager(f);}
        @Bean JdbcTemplate jdbc(DataSource ds){return new JdbcTemplate(ds);}
        @Bean ObjectMapper json(){return new ObjectMapper().findAndRegisterModules();}
        @Bean ReceptionRoomClient rooms(){var d=mock(ReceptionRoomClient.class);when(d.rooms()).thenReturn(ApiResponse.success(List.of(new ReceptionRoomClient.ReceptionRoom("R2-ROOM-01","CONSULTATION",true,"NOI_TONG_QUAT"),new ReceptionRoomClient.ReceptionRoom("R2-ROOM-02","CONSULTATION",true,"NOI_TONG_QUAT"))));when(d.department("NOI_TONG_QUAT")).thenReturn(ApiResponse.success(new ReceptionRoomClient.Department(DEPT,"NOI_TONG_QUAT","Synthetic department",true)));return d;}
        @Bean DirectoryClient directory(){var d=mock(DirectoryClient.class);var a=new DoctorAssignmentResponse();a.setUserId(ACTOR);a.setAssignedRoomId("R2-ROOM-01");a.setIsActive(true);when(d.getDoctorByUserId(ACTOR)).thenReturn(ApiResponse.success(a));return d;}
        @Bean PatientOwnershipClient owners(){var c=mock(PatientOwnershipClient.class);when(c.owner(any())).thenReturn(OWNER);return c;}
        @Bean QrTokenService qr(){return new QrTokenService(UUID.randomUUID()+UUID.randomUUID().toString(),"Asia/Ho_Chi_Minh");}
        @Bean QueueRoomConfigurationService policies(QueueConfigRepository r,ReceptionRoomClient d,JdbcTemplate j,ObjectMapper m){return new QueueRoomConfigurationService(r,d,j,m);}
        @Bean QueueEventService events(OutboxEventRepository r,ObjectMapper m){return new QueueEventService(r,m);}
        @Bean QueueManagementService queues(QueueConfigRepository c,QueueNumberSequenceRepository n,ServicePointSequenceRepository s,QueueEntryRepository e,IdempotencyRecordRepository i,QueueEventService events,QrTokenService qr,DirectoryClient d){return new QueueManagementService(c,n,s,e,i,events,qr,"Asia/Ho_Chi_Minh",d);}
    }
    @BeforeAll void open(){context=new AnnotationConfigApplicationContext(Config.class);queues=context.getBean(QueueManagementService.class);policies=context.getBean(QueueRoomConfigurationService.class);jdbc=context.getBean(JdbcTemplate.class);tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));}
    @AfterAll void close(){if(context!=null)context.close();}
    @BeforeEach void clear(){jdbc.update("DELETE FROM queue.idempotency_records");jdbc.update("DELETE FROM queue.queue_entries");jdbc.update("DELETE FROM queue.queue_number_sequences");jdbc.update("DELETE FROM queue.room_policy_audit");jdbc.update("DELETE FROM queue.queue_configs");}
    ManualIntakeRequest intake(UUID patient,String room){return new ManualIntakeRequest(patient,OWNER,DEPT,PriorityLevel.WALK_IN,room,"GENERAL_CONSULTATION");}
    QueueEntryResponse issue(String room){return queues.manualIntake(intake(UUID.randomUUID(),room),ACTOR,UUID.randomUUID().toString(),null,"Synthetic Patient");}
    void denied(int code,Runnable action){assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(code));}
    @Test void independentRoomsShareDepartmentWithoutSharingQueueOrPolicy(){var a=issue("R2-ROOM-01");var b=issue("R2-ROOM-02");assertThat(a.departmentId()).isEqualTo(b.departmentId());assertThat(a.roomCode()).isNotEqualTo(b.roomCode());assertThat(queues.roomDashboard("R2-ROOM-01").entries()).extracting(QueueEntryResponse::entryId).containsExactly(a.entryId());var saved=policies.save("R2-ROOM-01",new QueueRoomConfigurationService.Policy(0,25,5,MissedPolicy.REQUIRE_MANUAL,true),ACTOR);assertThat(saved.version()).isEqualTo(1);assertThat(policies.get("R2-ROOM-02").config().avgConsultationMinutes()).isEqualTo(15);denied(409,()->policies.save("R2-ROOM-01",new QueueRoomConfigurationService.Policy(0,15,2,MissedPolicy.REQUEUE_BACK,true),ACTOR));}
    @Test void concurrentReceptionRetryProducesOneNumberAndOneEvent()throws Exception{UUID patient=UUID.randomUUID();String key=UUID.randomUUID().toString();var pool=Executors.newFixedThreadPool(2);try{var results=pool.invokeAll(List.<Callable<UUID>>of(()->queues.manualIntake(intake(patient,"R2-ROOM-01"),ACTOR,key,null,"Synthetic Patient").entryId(),()->queues.manualIntake(intake(patient,"R2-ROOM-01"),ACTOR,key,null,"Synthetic Patient").entryId()));assertThat(results.get(0).get()).isEqualTo(results.get(1).get());}finally{pool.shutdownNow();}assertThat(jdbc.queryForObject("SELECT count(*) FROM queue.queue_entries",Integer.class)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM queue.outbox_events WHERE aggregate_id IN (SELECT id FROM queue.queue_entries)",Integer.class)).isEqualTo(1);}
    @Test void anotherRoomCannotIssueSecondActiveVisitForSamePatient(){UUID patient=UUID.randomUUID();queues.manualIntake(intake(patient,"R2-ROOM-01"),ACTOR,UUID.randomUUID().toString(),null,"Synthetic Patient");denied(409,()->queues.manualIntake(intake(patient,"R2-ROOM-02"),ACTOR,UUID.randomUUID().toString(),null,"Synthetic Patient"));}
    @Test void missedRecallManualRequeuePreservesLaneAndCanBeCalledAgain(){var entry=issue("R2-ROOM-01");policies.save("R2-ROOM-01",new QueueRoomConfigurationService.Policy(0,15,3,MissedPolicy.REQUIRE_MANUAL,true),ACTOR);queues.call(entry.entryId(),ACTOR,"DOCTOR",UUID.randomUUID().toString(),null);denied(409,()->queues.miss(entry.entryId(),null));queues.recall(entry.entryId(),ACTOR,null);queues.recall(entry.entryId(),ACTOR,null);queues.miss(entry.entryId(),null);assertThat(queues.roomDashboard("R2-ROOM-01").entries()).extracting(QueueEntryResponse::queueStatus).contains(QueueStatus.MISSED);denied(400,()->queues.requeue(entry.entryId(),new RequeueRequest(null),null));var requeued=queues.requeue(entry.entryId(),new RequeueRequest(RequeueRequest.Position.FRONT),null);assertThat(requeued.queueStatus()).isEqualTo(QueueStatus.CHECKED_IN);assertThat(requeued.callAttempts()).isZero();assertThat(queues.call(entry.entryId(),ACTOR,"DOCTOR",UUID.randomUUID().toString(),null).queueStatus()).isEqualTo(QueueStatus.CALLED);}
    @Test void calledWalkInHasVerifiedClinicalContextButUnassignedDoctorCannotReadIt(){var entry=issue("R2-ROOM-01");queues.call(entry.entryId(),ACTOR,"DOCTOR",UUID.randomUUID().toString(),null);var clinical=queues.clinicalContext(entry.entryId(),ACTOR,"DOCTOR");assertThat(clinical.appointmentId()).isNull();assertThat(clinical.patientName()).isEqualTo("Synthetic Patient");denied(403,()->queues.clinicalContext(entry.entryId(),UUID.randomUUID(),"DOCTOR"));}
    @Test void twoFutureTicketsAreAllowedButConcurrentCheckInActivatesOnlyOne()throws Exception{
        UUID patient=UUID.randomUUID();var a=scheduled(patient,"R2-ROOM-01");var b=scheduled(patient,"R2-ROOM-02");String qr=context.getBean(QrTokenService.class).issueHospitalQr("HOSPITAL-MAIN",queues.businessDate(),"MORNING").token();var pool=Executors.newFixedThreadPool(2);
        try{var results=pool.invokeAll(List.<Callable<Integer>>of(()->check(a,qr),()->check(b,qr)));assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder(200,409);}finally{pool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM queue.queue_entries WHERE status='CHECKED_IN'",Integer.class)).isEqualTo(1);
    }
    @Test void pausingReceptionKeepsBookedTicketsButBlocksNewCheckInAndWalkIn(){
        var active=issue("R2-ROOM-01");policies.save("R2-ROOM-01",new QueueRoomConfigurationService.Policy(0,15,3,MissedPolicy.REQUEUE_BACK,false),ACTOR);
        assertThat(queues.roomDashboard("R2-ROOM-01").receptionActive()).isFalse();
        assertThat(queues.roomDashboard("R2-ROOM-01").entries()).extracting(QueueEntryResponse::entryId).contains(active.entryId());
        denied(409,()->queues.call(active.entryId(),ACTOR,"DOCTOR",UUID.randomUUID().toString(),null));
        UUID patient=UUID.randomUUID();QueueEntry entry=tx.execute(t->{var config=queues.requireScheduledRoom("R2-ROOM-01",DEPT);assertThat(config.isActive()).isFalse();var booked=queues.createAppointmentEntry(config,queues.businessDate(),LocalTime.of(15,0),"15:00-15:30","NOI_TONG_QUAT","R2-ROOM-01",UUID.randomUUID(),patient,OWNER);return context.getBean(QueueEntryRepository.class).saveAndFlush(booked);});
        String qr=context.getBean(QrTokenService.class).issueHospitalQr("HOSPITAL-MAIN",queues.businessDate(),"MORNING").token();assertThat(check(entry,qr)).isEqualTo(409);
        denied(409,()->issue("R2-ROOM-01"));assertThat(queues.appointmentStatus(entry.getAppointmentId(),OWNER,"PATIENT").queueStatus()).isEqualTo(QueueStatus.WAITING);
    }
    QueueEntry scheduled(UUID patient,String room){return tx.execute(t->{var config=policies.ensure(room,DEPT);var entry=queues.createAppointmentEntry(config,queues.businessDate(),LocalTime.of(15,0),"15:00-15:30","NOI_TONG_QUAT",room,UUID.randomUUID(),patient,OWNER);return context.getBean(QueueEntryRepository.class).saveAndFlush(entry);});}
    int check(QueueEntry e,String qr){try{queues.checkIn(new CheckInRequest(null,null,null,null,null,e.getAppointmentId(),qr,10.7769,106.7009,5.0),OWNER,"PATIENT",null);return 200;}catch(BusinessException error){return error.getStatus();}}
}
