package com.careflow.lab.service;
import com.careflow.lab.client.*;
import com.careflow.lab.dto.request.*;
import com.careflow.lab.model.*;
import com.careflow.lab.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.beans.factory.annotation.Autowired;
import javax.sql.DataSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@SpringJUnitConfig(LabCreationPostgresTest.Config.class)
@EnabledIfEnvironmentVariable(named="R3_DB_TESTS",matches="1")
class LabCreationPostgresTest {
 @Configuration @EnableTransactionManagement @EnableJpaRepositories(basePackages="com.careflow.lab.repository") static class Config {
  @Bean DataSource dataSource(){var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:15432/careflow_r3_lab_test",System.getenv("R3_DB_USER"),System.getenv("R3_DB_PASSWORD"));Flyway.configure().dataSource(ds).schemas("public").load().migrate();return ds;}
  @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){var em=new LocalContainerEntityManagerFactoryBean();em.setDataSource(ds);em.setPackagesToScan("com.careflow.lab.model");em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());em.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return em;}
  @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory em){return new JpaTransactionManager(em);}
  @Bean JdbcTemplate jdbcTemplate(DataSource ds){return new JdbcTemplate(ds);}
  @Bean ObjectMapper objectMapper(){return new ObjectMapper().findAndRegisterModules();}
  @Bean RabbitTemplate rabbit(){return mock(RabbitTemplate.class);}
  @Bean LabAccessService access(){return mock(LabAccessService.class);}
  @Bean LabCatalogService catalog(){return mock(LabCatalogService.class);}
  @Bean LabQueueSourceClient source(){return mock(LabQueueSourceClient.class);}
  @Bean LabEventOutbox outbox(JdbcTemplate jdbc,ObjectMapper json,RabbitTemplate rabbit,PlatformTransactionManager manager){return new LabEventOutbox(jdbc,json,rabbit,manager);}
  @Bean LabEventPublisher events(RabbitTemplate rabbit){return new RabbitLabEventPublisher(rabbit);}
  @Bean LabOrderService service(LabOrderRepository orders,LabOrderItemRepository items,LabEventPublisher events,ObjectMapper json,LabAccessService access,LabCatalogService catalog){return new LabOrderService(orders,items,events,json,access,catalog);}
 }
 @Autowired LabOrderService service;@Autowired LabAccessService access;@Autowired LabCatalogService catalog;@Autowired JdbcTemplate jdbc;@Autowired jakarta.persistence.EntityManagerFactory factory;
 UUID consultation=UUID.randomUUID(),patient=UUID.randomUUID(),doctor=UUID.randomUUID(),source=UUID.randomUUID();
 @BeforeEach void setup(){jdbc.execute("TRUNCATE lab_order_items,lab_orders,lab_event_outbox CASCADE");reset(access,catalog);when(access.requireConsultation(consultation,patient,doctor,"DOCTOR",true)).thenReturn(new LabConsultationClient.Context(consultation,patient,doctor,"IN_PROGRESS",source,null));when(catalog.requireItem(any())).thenReturn(new LabCatalogService.Definition("CBC","Synthetic CBC","LAB-HEMATOLOGY-01",null));}
 CreateLabOrderRequest request(){return new CreateLabOrderRequest(consultation,patient,List.of(new LabOrderItemRequest("CBC","Untrusted name","LAB-HEMATOLOGY-01",true,null)),"SYNTHETIC",false,null,UUID.randomUUID());}
 @Test void generatedIdentifiersVersionAndOutboxPersistTogether(){var created=service.createOrder(request(),doctor,"DOCTOR","synthetic");assertThat(created.id()).isNotNull();assertThat(created.items().getFirst().id()).isNotNull();assertThat(created.departmentId()).isNull();assertThat(jdbc.queryForObject("SELECT source_queue_entry_id FROM lab_orders WHERE id=?",UUID.class,created.id())).isEqualTo(source);assertThat(jdbc.queryForObject("SELECT version FROM lab_orders WHERE id=?",Long.class,created.id())).isZero();assertThat(jdbc.queryForObject("SELECT payload->'payload'->>'sourceQueueEntryId' FROM lab_event_outbox WHERE aggregate_id=?",String.class,created.id())).isEqualTo(source.toString());}
 @Test void outboxFailureRollsBackOrderAndItems(){jdbc.execute("ALTER TABLE lab_event_outbox ADD CONSTRAINT r3_lab_fail CHECK(event_type<>'LabOrderReadyForExecution')");try{assertThatThrownBy(()->service.createOrder(request(),doctor,"DOCTOR","synthetic")).isInstanceOf(RuntimeException.class);}finally{jdbc.execute("ALTER TABLE lab_event_outbox DROP CONSTRAINT r3_lab_fail");}assertThat(jdbc.queryForObject("SELECT count(*) FROM lab_orders",Integer.class)).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM lab_order_items",Integer.class)).isZero();}
 @Test void staleWriterCannotOverwriteCurrentClinicalData(){var created=service.createOrder(request(),doctor,"DOCTOR","synthetic");var a=factory.createEntityManager();var b=factory.createEntityManager();try{a.getTransaction().begin();b.getTransaction().begin();var first=a.find(LabOrder.class,created.id());var stale=b.find(LabOrder.class,created.id());first.setClinicalNote("CURRENT SYNTHETIC");a.getTransaction().commit();stale.setClinicalNote("STALE SYNTHETIC");assertThatThrownBy(()->b.getTransaction().commit()).isInstanceOf(RuntimeException.class);assertThat(jdbc.queryForObject("SELECT clinical_note FROM lab_orders WHERE id=?",String.class,created.id())).isEqualTo("CURRENT SYNTHETIC");}finally{if(a.getTransaction().isActive())a.getTransaction().rollback();if(b.getTransaction().isActive())b.getTransaction().rollback();a.close();b.close();}}
}
