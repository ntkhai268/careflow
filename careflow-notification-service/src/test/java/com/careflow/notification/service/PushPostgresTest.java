package com.careflow.notification.service;
import com.careflow.common.security.PatientOwnershipClient;
import com.careflow.notification.config.FirebasePushProperties;
import com.careflow.notification.domain.*;
import com.careflow.notification.dto.RegisterDeviceRequest;
import com.careflow.notification.repository.*;
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
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@EnabledIfEnvironmentVariable(named="R4_DB_TESTS", matches="1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PushPostgresTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc; DeviceInstallationService devices; PushDeliveryService deliveries;
    DeviceInstallationRepository registrations; NotificationRepository notifications;
    PushSender sender; PushSessionClient sessions; PatientOwnershipClient owners;
    @Configuration @EnableTransactionManagement @EnableJpaRepositories(basePackageClasses=NotificationRepository.class)
    static class Config {
        @Bean DataSource dataSource() {
            var ds = new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:15432/careflow_r4_notification_test", System.getenv("R4_DB_USER"), System.getenv("R4_DB_PASSWORD"));
            Flyway.configure().dataSource(ds).schemas("notification").defaultSchema("notification").locations("classpath:db/migration").load().migrate(); return ds;
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var f=new LocalContainerEntityManagerFactoryBean(); f.setDataSource(ds); f.setPackagesToScan("com.careflow.notification.domain"); f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate", "hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")); return f;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory f) {return new JpaTransactionManager(f);}
        @Bean JdbcTemplate jdbc(DataSource ds) {return new JdbcTemplate(ds);}
        @Bean DeviceInstallationService devices(DeviceInstallationRepository r) {return new DeviceInstallationService(r);}
        @Bean PushSessionClient sessions() {return mock(PushSessionClient.class);}
        @Bean PatientOwnershipClient owners() {return mock(PatientOwnershipClient.class);}
        @Bean PushSender sender() {return mock(PushSender.class);}
        @Bean FirebasePushProperties properties() {var p=new FirebasePushProperties();p.setEnabled(true);p.setMaxAttempts(3);return p;}
        @Bean PushDeliveryService deliveries(FirebasePushProperties p, DeviceInstallationRepository d, PushDeliveryRepository r, NotificationRepository n, PushSender s) {return new PushDeliveryService(p,d,r,n,s);}
    }
    @BeforeAll void open() {context=new AnnotationConfigApplicationContext(Config.class);jdbc=context.getBean(JdbcTemplate.class);devices=context.getBean(DeviceInstallationService.class);deliveries=context.getBean(PushDeliveryService.class);registrations=context.getBean(DeviceInstallationRepository.class);notifications=context.getBean(NotificationRepository.class);sender=context.getBean(PushSender.class);sessions=context.getBean(PushSessionClient.class);owners=context.getBean(PatientOwnershipClient.class);}
    @AfterAll void close() {if(context!=null)context.close();}
    @BeforeEach void clear() {jdbc.update("TRUNCATE notification.push_deliveries, notification.device_installations, notification.notifications CASCADE");reset(sender,sessions,owners);when(sessions.active(any(),any())).thenReturn(true);}
    RegisterDeviceRequest request(String token) {return new RegisterDeviceRequest("synthetic-device",token,DevicePlatform.IOS,"r4-test");}
    Notification notification(UUID user) {var n=new Notification();n.setId(UUID.randomUUID());n.setRecipientUserId(user);n.setPatientId(UUID.randomUUID());n.setSourceEventId(UUID.randomUUID());n.setSourceEventType("PatientCalled");n.setType(NotificationType.QUEUE_CALLED);n.setTitle("Synthetic");n.setBody("Synthetic fixture, no clinical data");n.setStatus(NotificationStatus.DELIVERED);n.setCreatedAt(Instant.now());n=notifications.saveAndFlush(n);when(owners.owner(n.getPatientId())).thenReturn(user);return n;}
    @Test void refreshKeepsBindingButRotationChangesIt() {
        UUID user=UUID.randomUUID();var a=devices.register(user,UUID.randomUUID(),request("TOKEN_A"));var b=devices.register(user,UUID.randomUUID(),request("TOKEN_A"));var c=devices.register(user,UUID.randomUUID(),request("TOKEN_B"));
        assertThat(a.bindingId()).isEqualTo(b.bindingId()).isNotEqualTo(c.bindingId());assertThat(registrations.findAll()).hasSize(1);
    }
    @Test void concurrentTokenTransferHasOneEnabledOwnerAndPreservesAudit() throws Exception {
        var pool=Executors.newFixedThreadPool(2);try {var results=pool.invokeAll(List.<Callable<UUID>>of(()->devices.register(UUID.randomUUID(),UUID.randomUUID(),request("SHARED_TOKEN")).id(),()->devices.register(UUID.randomUUID(),UUID.randomUUID(),request("SHARED_TOKEN")).id()));for(var r:results)assertThat(r.get()).isNotNull();}finally{pool.shutdownNow();}
        assertThat(registrations.findAll()).hasSize(2);assertThat(registrations.findAll().stream().filter(DeviceInstallation::isEnabled).count()).isEqualTo(1);
    }
    @Test void transferBlocksAlreadyQueuedMessageWithoutDeletingInbox() {
        UUID a=UUID.randomUUID();devices.register(a,UUID.randomUUID(),request("SHARED_TOKEN"));var n=notification(a);deliveries.enqueue(n);devices.register(UUID.randomUUID(),UUID.randomUUID(),request("SHARED_TOKEN"));deliveries.dispatchDue();
        verifyNoInteractions(sender);assertThat(notifications.findById(n.getId())).isPresent();assertThat(jdbc.queryForObject("SELECT status FROM notification.push_deliveries",String.class)).isEqualTo("DEAD");
    }
    @Test void providerFailureIsDurableAndDoesNotRollBackInbox() {
        UUID user=UUID.randomUUID();devices.register(user,UUID.randomUUID(),request("TOKEN"));var n=notification(user);deliveries.enqueue(n);doThrow(new PushSendException("Synthetic transient outage",false,null)).when(sender).send(any(),any(),any());deliveries.dispatchDue();
        assertThat(notifications.findById(n.getId())).isPresent();assertThat(jdbc.queryForObject("SELECT status FROM notification.push_deliveries",String.class)).isEqualTo("FAILED");assertThat(registrations.findAll().getFirst().isEnabled()).isTrue();
    }
    @Test void concurrentDispatchersClaimEachRowOnce() throws Exception {
        UUID user=UUID.randomUUID();devices.register(user,UUID.randomUUID(),request("TOKEN"));deliveries.enqueue(notification(user));var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(invocation->{entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Timed out");return null;}).when(sender).send(any(),any(),any());
        var pool=Executors.newFixedThreadPool(2);try{var first=pool.submit(deliveries::dispatchDue);assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();var second=pool.submit(deliveries::dispatchDue);second.get(5,TimeUnit.SECONDS);release.countDown();first.get(5,TimeUnit.SECONDS);}finally{release.countDown();pool.shutdownNow();}
        verify(sender,times(1)).send(any(),any(),any());assertThat(jdbc.queryForObject("SELECT status FROM notification.push_deliveries",String.class)).isEqualTo("SENT");
    }
    @Test void historyCursorIsStableWhenTimestampsTieAndRejectsForeignCursor() {
        UUID user=UUID.randomUUID();var a=notification(user);var b=notification(user);b.setCreatedAt(a.getCreatedAt());notifications.saveAndFlush(b);notification(UUID.randomUUID());
        var first=notifications.findByRecipientUserIdOrderByCreatedAtDesc(user,org.springframework.data.domain.PageRequest.of(0,1)).getFirst();
        assertThat(notifications.findOlder(user,first.getCreatedAt(),first.getId(),org.springframework.data.domain.PageRequest.of(0,50))).hasSize(1);
        var controller=new com.careflow.notification.controller.NotificationHistoryController(notifications);
        assertThatThrownBy(()->controller.history(UUID.randomUUID(),first.getId(),50)).isInstanceOf(com.careflow.common.exception.ResourceNotFoundException.class);
    }
}
