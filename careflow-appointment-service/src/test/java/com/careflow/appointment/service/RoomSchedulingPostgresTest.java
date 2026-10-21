package com.careflow.appointment.service;

import com.careflow.appointment.client.*;
import com.careflow.appointment.client.dto.*;
import com.careflow.appointment.dto.request.CreateAppointmentRequest;
import com.careflow.appointment.model.*;
import com.careflow.appointment.repository.*;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.security.PatientOwnershipClient;
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
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Runs only against the explicitly named, disposable loopback database, never the shared DB. */
@EnabledIfEnvironmentVariable(named="R2_DB_TESTS",matches="1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoomSchedulingPostgresTest {
    AnnotationConfigApplicationContext context;
    AppointmentService bookings;RoomScheduleService schedules;JdbcTemplate jdbc;
    final LocalDate date=LocalDate.now(RoomScheduleService.ZONE).plusDays(5);
    static final UUID DOCTOR1=UUID.randomUUID(),DOCTOR2=UUID.randomUUID();
    @Configuration @EnableTransactionManagement @EnableJpaRepositories(basePackageClasses=AppointmentRepository.class)
    static class Config {
        @Bean DataSource dataSource(){
            var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:15432/careflow_r2_appointment_test",System.getenv("R2_DB_USER"),System.getenv("R2_DB_PASSWORD"));
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();return ds;
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){
            var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(ds);factory.setPackagesToScan("com.careflow.appointment.model");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory){return new JpaTransactionManager(factory);}
        @Bean JdbcTemplate jdbcTemplate(DataSource ds){return new JdbcTemplate(ds);}
        @Bean ObjectMapper json(){return new ObjectMapper().findAndRegisterModules();}
        @Bean DirectoryClient directory(){
            var directory=mock(DirectoryClient.class);AppointmentPricingFixture.priced(directory);
            when(directory.getDepartmentByCode("NOI_TONG_QUAT")).thenReturn(ApiResponse.success(DepartmentResponse.builder().id(Department.NOI_TONG_QUAT.getId()).code("NOI_TONG_QUAT").isActive(true).build()));
            when(directory.getAllRooms()).thenReturn(ApiResponse.success(List.of(room("ROOM-01"),room("ROOM-12"))));
            when(directory.getDoctors("NOI_TONG_QUAT")).thenReturn(ApiResponse.success(List.of(doctor(DOCTOR1,"ROOM-01"),doctor(DOCTOR2,"ROOM-12"))));
            return directory;
        }
        static RoomResponse room(String id){return RoomResponse.builder().id(id).departmentCode("NOI_TONG_QUAT").displayName(id).roomType("CONSULTATION").isActive(true).build();}
        static DoctorProfileResponse doctor(UUID id,String room){return DoctorProfileResponse.builder().userId(id).fullName("Synthetic doctor").departmentCode("NOI_TONG_QUAT").assignedRoomId(room).isActive(true).build();}
        @Bean RoomScheduleService schedules(JdbcTemplate jdbc,ObjectMapper json,DirectoryClient directory){return new RoomScheduleService(jdbc,json,directory,5);}
        @Bean AppointmentCommandStore commands(JdbcTemplate jdbc){return new AppointmentCommandStore(jdbc);}
        @Bean PatientOwnershipClient ownership(){return mock(PatientOwnershipClient.class);}
        @Bean AppointmentService bookings(AppointmentRepository repo,AppointmentSlotLockRepository locks,DirectoryClient directory,RoomScheduleService schedules,AppointmentCommandStore commands){return new AppointmentService(repo,mock(AppointmentEventService.class),directory,mock(PatientClient.class),locks,mock(AppointmentConsultationClient.class),schedules,commands);}
    }
    @BeforeAll void open(){context=new AnnotationConfigApplicationContext(Config.class);bookings=context.getBean(AppointmentService.class);schedules=context.getBean(RoomScheduleService.class);jdbc=context.getBean(JdbcTemplate.class);}
    @AfterAll void close(){if(context!=null)context.close();}
    @BeforeEach void clear(){jdbc.update("DELETE FROM appointment_booking_commands");jdbc.update("DELETE FROM appointments");jdbc.update("DELETE FROM appointment_schedule_revisions");jdbc.update("DELETE FROM appointment_room_schedules");}
    RoomScheduleService.Definition definition(int capacity){return new RoomScheduleService.Definition(LocalDate.now(RoomScheduleService.ZONE),"Asia/Ho_Chi_Minh",List.of(1,2,3,4,5,6,7),List.of(new RoomScheduleService.Window("08:00","09:00",30,capacity)),List.of());}
    CreateAppointmentRequest request(UUID patient){return CreateAppointmentRequest.builder().patientId(patient).patientName("Synthetic patient").department("NOI_TONG_QUAT").appointmentDate(date).timeSlot("08:00-08:30").build();}
    void capacity(int capacity){for(String room:List.of("ROOM-01","ROOM-12"))schedules.save(room,new RoomScheduleService.Update(0,definition(capacity)),UUID.randomUUID());}
    @Test void scheduleSurvivesNewServiceAndHonorsWeekdaysAndExceptions(){
        var d=definition(2);d=new RoomScheduleService.Definition(d.effectiveFrom(),d.timeZone(),d.weekdays(),d.windows(),List.of(new RoomScheduleService.ExceptionDay(date,true,List.of())));
        schedules.save("ROOM-01",new RoomScheduleService.Update(0,d),UUID.randomUUID());
        var fresh=new RoomScheduleService(jdbc,new ObjectMapper().findAndRegisterModules(),context.getBean(DirectoryClient.class),5);
        assertThat(fresh.get("ROOM-01").version()).isEqualTo(1);assertThat(fresh.slots("ROOM-01",date)).isEmpty();assertThat(fresh.slots("ROOM-01",date.plusDays(1))).hasSize(2);
        assertThatThrownBy(()->schedules.save("ROOM-01",new RoomScheduleService.Update(0,definition(3)),UUID.randomUUID())).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(409));
    }
    @Test void concurrentBookingsUseBothRoomsAndNeverExceedCapacity()throws Exception{
        capacity(2);var pool=Executors.newFixedThreadPool(8);
        try{
            var tasks=new ArrayList<Callable<Integer>>();for(int i=0;i<12;i++)tasks.add(()->{try{bookings.createAppointment(request(UUID.randomUUID()),UUID.randomUUID(),UUID.randomUUID().toString(),null);return 201;}catch(BusinessException e){return e.getStatus();}});
            var results=pool.invokeAll(tasks);int success=0;for(var result:results){int status=result.get();assertThat(status).isIn(201,409);if(status==201)success++;}assertThat(success).isEqualTo(4);
        }finally{pool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM appointments",Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForList("SELECT count(*) AS total FROM appointments GROUP BY room_id",Integer.class)).containsExactlyInAnyOrder(2,2);
    }
    @Test void concurrentSamePatientCannotReserveTwoRooms()throws Exception{
        capacity(2);UUID patient=UUID.randomUUID();var pool=Executors.newFixedThreadPool(2);
        try{var outcomes=pool.invokeAll(List.<Callable<Integer>>of(()->book(patient),()->book(patient)));assertThat(outcomes.stream().map(f->{try{return f.get();}catch(Exception e){throw new RuntimeException(e);}}).toList()).containsExactlyInAnyOrder(201,409);}finally{pool.shutdownNow();}
    }
    int book(UUID patient){try{bookings.createAppointment(request(patient),UUID.randomUUID(),UUID.randomUUID().toString(),null);return 201;}catch(BusinessException e){return e.getStatus();}}
    @Test void durableCommandReplaysAfterNewServiceAndRejectsChangedPayload(){
        capacity(2);UUID owner=UUID.randomUUID(),patient=UUID.randomUUID();String key=UUID.randomUUID().toString();var first=bookings.createAppointment(request(patient),owner,null,key);
        var fresh=new AppointmentService(context.getBean(AppointmentRepository.class),mock(AppointmentEventService.class),context.getBean(DirectoryClient.class),mock(PatientClient.class),context.getBean(AppointmentSlotLockRepository.class),mock(AppointmentConsultationClient.class),schedules,new AppointmentCommandStore(jdbc));
        var tx=new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        var replay=tx.execute(t->fresh.createAppointment(request(patient),owner,null,key));assertThat(replay.getId()).isEqualTo(first.getId());
        assertThatThrownBy(()->tx.execute(t->fresh.createAppointment(request(UUID.randomUUID()),owner,null,key))).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(409));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM appointments",Integer.class)).isEqualTo(1);
    }
    @Test void scheduleCannotRemoveOrShrinkAnExistingReservation(){
        capacity(2);var booked=bookings.createAppointment(request(UUID.randomUUID()),UUID.randomUUID(),UUID.randomUUID().toString(),null);
        var d=definition(2);var closed=new RoomScheduleService.Definition(d.effectiveFrom(),d.timeZone(),d.weekdays(),d.windows(),List.of(new RoomScheduleService.ExceptionDay(date,true,List.of())));
        assertThatThrownBy(()->schedules.save(booked.getRoomId(),new RoomScheduleService.Update(1,closed),UUID.randomUUID())).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(409));
        assertThat(schedules.get(booked.getRoomId()).version()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT status FROM appointments WHERE id=?",String.class,booked.getId())).isEqualTo("CONFIRMED");
    }
}
