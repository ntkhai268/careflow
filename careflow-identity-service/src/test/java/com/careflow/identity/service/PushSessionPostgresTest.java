package com.careflow.identity.service;
import com.careflow.identity.domain.*;
import com.careflow.identity.repository.*;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="R4_DB_TESTS",matches="1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PushSessionPostgresTest {
    AnnotationConfigApplicationContext context;PushSessionStatusService service;UserRepository users;RefreshTokenRepository tokens;
    @Configuration @EnableTransactionManagement @EnableJpaRepositories(basePackageClasses=RefreshTokenRepository.class)
    static class Config {
        @Bean DataSource dataSource() {var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:15432/careflow_r4_identity_test",System.getenv("R4_DB_USER"),System.getenv("R4_DB_PASSWORD"));Flyway.configure().dataSource(ds).schemas("identity").defaultSchema("identity").locations("classpath:db/migration").load().migrate();return ds;}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("com.careflow.identity.domain");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return f;}
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory f) {return new JpaTransactionManager(f);}
        @Bean PushSessionStatusService service(RefreshTokenRepository r) {return new PushSessionStatusService(r);}
    }
    @BeforeAll void open() {context=new AnnotationConfigApplicationContext(Config.class);service=context.getBean(PushSessionStatusService.class);users=context.getBean(UserRepository.class);tokens=context.getBean(RefreshTokenRepository.class);}
    @AfterAll void close() {if(context!=null)context.close();}
    User account() {var u=new User();u.setId(UUID.randomUUID());u.setUsername("r4_"+UUID.randomUUID().toString().substring(0,8));u.setEmail(u.getUsername()+"@example.test");u.setPasswordHash("SYNTHETIC_NOT_A_LOGIN_HASH");u.setRole(UserRole.PATIENT);u.setStatus(UserStatus.ACTIVE);return users.saveAndFlush(u);}
    RefreshToken token(User user) {var t=new RefreshToken();t.setId(UUID.randomUUID());t.setUser(user);t.setTokenHash(UUID.randomUUID().toString());t.setDpopJkt("t".repeat(43));t.setAbsoluteExpiresAt(Instant.now().plusSeconds(3600));t.setExpiresAt(Instant.now().plusSeconds(600));return tokens.saveAndFlush(t);}
    @Test void rotatedSessionCanBeReadInReadOnlyTransactionAndLogoutStopsIt() {var u=account();var root=token(u);var leaf=token(u);root.revoke(Instant.now(),leaf.getTokenHash());tokens.saveAndFlush(root);assertThat(service.active(u.getId(),root.getId())).isTrue();leaf.revoke(Instant.now(),null);tokens.saveAndFlush(leaf);assertThat(service.active(u.getId(),root.getId())).isFalse();}
    @Test void crossAccountRotationIsRejectedAgainstRealDatabase() {var u=account();var root=token(u);var foreign=token(account());root.revoke(Instant.now(),foreign.getTokenHash());tokens.saveAndFlush(root);assertThat(service.active(u.getId(),root.getId())).isFalse();}
}
