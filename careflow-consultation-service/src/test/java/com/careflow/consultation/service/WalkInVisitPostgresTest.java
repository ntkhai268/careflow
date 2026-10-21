package com.careflow.consultation.service;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.security.PatientOwnershipClient;
import com.careflow.consultation.client.*;
import com.careflow.consultation.dto.request.CreateConsultationRequest;
import com.careflow.consultation.handover.DoctorWorkloadLock;
import com.careflow.consultation.mapper.ConsultationMapper;
import com.careflow.consultation.repository.ConsultationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
@EnabledIfEnvironmentVariable(named="R2_DB_TESTS",matches="1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WalkInVisitPostgresTest {
    static final UUID PATIENT=UUID.randomUUID();AnnotationConfigApplicationContext context;ConsultationService service;JdbcTemplate jdbc;
    @Configuration @EnableTransactionManagement @EnableJpaRepositories(basePackageClasses=ConsultationRepository.class)
    static class Config {
        @Bean DataSource dataSource(){var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:15432/careflow_r2_consultation_test",System.getenv("R2_DB_USER"),System.getenv("R2_DB_PASSWORD"));Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();return ds;}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("com.careflow.consultation.model");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return f;}
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory f){return new JpaTransactionManager(f);}
        @Bean JdbcTemplate jdbc(DataSource ds){return new JdbcTemplate(ds);}
        @Bean PatientOwnershipClient owner(){return mock(PatientOwnershipClient.class);}
        @Bean DoctorWorkloadLock locks(JdbcTemplate j){return new DoctorWorkloadLock(j);}
        @Bean ConsultationQueueClient queue(){var q=mock(ConsultationQueueClient.class);when(q.clinicalContext(any())).thenAnswer(call->ApiResponse.success(new ConsultationQueueClient.ClinicalContext(call.getArgument(0),PATIENT,null,"Synthetic Patient","ROOM-01","INITIAL","CALLED",null)));return q;}
        @Bean ConsultationService service(ConsultationRepository r,ConsultationQueueClient q,DoctorWorkloadLock locks){return new ConsultationService(r,new ConsultationMapper(),mock(RabbitTemplate.class),mock(AppointmentClient.class),q,mock(DirectoryClient.class),locks,mock(ConsultationLabClient.class));}
    }
    @BeforeAll void open(){context=new AnnotationConfigApplicationContext(Config.class);service=context.getBean(ConsultationService.class);jdbc=context.getBean(JdbcTemplate.class);}
    @AfterAll void close(){if(context!=null)context.close();}
    @BeforeEach void clear(){jdbc.update("DELETE FROM consultations");}
    UUID start(UUID entry,UUID doctor){return service.createConsultation(new CreateConsultationRequest(null,entry,PATIENT,doctor),doctor,"DOCTOR").getId();}
    @Test void twoRequestsForSameVisitReturnSamePersistedConsultation()throws Exception{UUID entry=UUID.randomUUID(),doctor=UUID.randomUUID();var pool=Executors.newFixedThreadPool(2);try{var results=pool.invokeAll(List.<Callable<UUID>>of(()->start(entry,doctor),()->start(entry,doctor)));assertThat(results.get(0).get()).isEqualTo(results.get(1).get());}finally{pool.shutdownNow();}assertThat(jdbc.queryForObject("SELECT count(*) FROM consultations",Integer.class)).isEqualTo(1);}
    @Test void twoDoctorsCannotClaimSameQueueEntry()throws Exception{UUID entry=UUID.randomUUID();var pool=Executors.newFixedThreadPool(2);try{Callable<Integer> task=()->{try{start(entry,UUID.randomUUID());return 201;}catch(BusinessException e){return e.getStatus();}};var results=pool.invokeAll(List.of(task,task));assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder(201,403);}finally{pool.shutdownNow();}assertThat(jdbc.queryForObject("SELECT count(*) FROM consultations",Integer.class)).isEqualTo(1);}
    @Test void samePatientDoesNotMakeDifferentVisitsEquivalent(){UUID doctor=UUID.randomUUID();start(UUID.randomUUID(),doctor);assertThatThrownBy(()->start(UUID.randomUUID(),doctor)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(400));}
}
