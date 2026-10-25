package com.careflow.identity.biometric;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.*;
import com.careflow.identity.repository.*;
import com.careflow.identity.security.JwtService;
import com.careflow.identity.service.*;
import com.careflow.identity.recovery.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="RECOVERY_TEST_JDBC",matches="jdbc:postgresql://127\\.0\\.0\\.1:15432/careflow_email_recovery_test")
@SpringBootTest(classes=BiometricDevicePostgresTest.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
 properties={"eureka.client.enabled=false","spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false",
 "spring.flyway.enabled=true","spring.flyway.schemas=identity","spring.flyway.default-schema=identity"})
class BiometricDevicePostgresTest {
    @Configuration @EnableAutoConfiguration(exclude=com.careflow.common.security.ActiveSessionAutoConfiguration.class)
    @EntityScan(basePackageClasses={User.class,PasswordResetCode.class})
    @EnableJpaRepositories(basePackages={"com.careflow.identity.repository","com.careflow.identity.recovery"})
    @Import({BiometricDeviceService.class,RefreshTokenService.class,PasswordRecoveryService.class})
    static class Config {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(4); }
        @Bean JwtService jwt() { var mock=mock(JwtService.class);when(mock.issue(any(),any())).thenReturn("test-access");return mock; }
        @Bean Mail mail() { return new Mail(); }
    }
    static class Mail implements RecoveryEmailSender {
        final Map<UUID,String> codes=new ConcurrentHashMap<>();
        public boolean configured() { return true; }
        public void sendCode(String email,String code,UUID id) {}
        public void sendChanged(String email,UUID id) {}
        @EventListener public void requested(PasswordRecoveryService.CodeRequested event) { codes.put(event.requestId(),event.code()); }
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("RECOVERY_TEST_JDBC"));
        p.add("spring.datasource.username",()->System.getenv("RECOVERY_TEST_USER"));
        p.add("spring.datasource.password",()->System.getenv("RECOVERY_TEST_PASSWORD"));
    }
    @Autowired BiometricDeviceService devices;
    @Autowired BiometricDeviceRepository records;
    @Autowired RefreshTokenService sessions;
    @Autowired RefreshTokenRepository tokens;
    @Autowired UserRepository users;
    @Autowired PasswordRecoveryService recovery;
    @Autowired JdbcTemplate jdbc;
    @Autowired Mail mail;
    @Autowired PasswordEncoder passwords;
    User user;final String jkt="d".repeat(43),password="Password123!";
    @BeforeEach void account() {
        user=new User();user.setId(UUID.randomUUID());user.setUsername("device_"+UUID.randomUUID().toString().substring(0,8));
        user.setEmail(user.getUsername()+"@example.test");user.setPasswordHash(passwords.encode(password));user.setStatus(UserStatus.ACTIVE);user.setRole(UserRole.PATIENT);
        user=users.saveAndFlush(user);
    }
    @Test void repeatedLogoutAndBiometricLoginCreatesFreshSessionsAndKeepsDevice() {
        var registered=devices.enroll(user.getId(),password,jkt);var initial=sessions.issue(user,jkt);String current=initial.value();
        for(int i=0;i<3;i++) {
            sessions.revoke(current,jkt);
            var login=devices.login(registered.credentialId(),registered.deviceToken(),jkt);
            assertThat(login.refreshToken()).isNotEqualTo(current);current=login.refreshToken();
            assertThat(records.findById(registered.credentialId()).orElseThrow().getRevokedAt()).isNull();
        }
        assertThat(tokens.findById(initial.sessionId()).orElseThrow().isRevoked()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from identity.refresh_tokens where user_id=? and revoked_at is null",Integer.class,user.getId())).isEqualTo(1);
        assertThat(records.findById(registered.credentialId()).orElseThrow().getSecretHash()).doesNotContain(registered.deviceToken());
    }
    @Test void disableKeepsCurrentSessionButPreventsFutureBiometricLogin() {
        var registered=devices.enroll(user.getId(),password,jkt);devices.login(registered.credentialId(),registered.deviceToken(),jkt);
        devices.disable(user.getId(),registered.credentialId(),jkt);
        assertThatThrownBy(()->devices.login(registered.credentialId(),registered.deviceToken(),jkt)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select count(*) from identity.refresh_tokens where user_id=? and revoked_at is null",Integer.class,user.getId())).isEqualTo(1);
    }
    @Test void reenrollmentReplacesOnlyThisDeviceCredential() {
        var old=devices.enroll(user.getId(),password,jkt);var other=devices.enroll(user.getId(),password,"b".repeat(43));
        var replacement=devices.enroll(user.getId(),password,jkt);
        assertThatThrownBy(()->devices.login(old.credentialId(),old.deviceToken(),jkt)).isInstanceOf(BusinessException.class);
        devices.login(replacement.credentialId(),replacement.deviceToken(),jkt);devices.login(other.credentialId(),other.deviceToken(),"b".repeat(43));
    }
    @Test void emailPasswordResetRevokesEveryBiometricRegistration() {
        var registered=devices.enroll(user.getId(),password,jkt);sessions.issue(user,jkt);
        var request=recovery.request(user.getEmail(),jkt);
        jdbc.update("update identity.password_reset_codes set delivery_state='SENT' where challenge_id=?",request.requestId());
        var permission=recovery.verify(request.requestId(),mail.codes.get(request.requestId()),jkt);
        recovery.confirm(request.requestId(),permission.resetToken(),"NewPassword123!",jkt);
        assertThatThrownBy(()->devices.login(registered.credentialId(),registered.deviceToken(),jkt)).isInstanceOf(BusinessException.class);
        assertThat(passwords.matches("NewPassword123!",users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
    }
    @Test void globalRevokeWinsAgainstConcurrentBiometricLogin() throws Exception {
        var registration=devices.enroll(user.getId(),password,jkt);
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var login=pool.submit(()->{start.await();try { devices.login(registration.credentialId(),registration.deviceToken(),jkt);return 200; }catch(BusinessException e){return e.getStatus();}});
            var revoke=pool.submit(()->{start.await();sessions.revokeAll(user);return true;});start.countDown();
            assertThat(login.get(10,TimeUnit.SECONDS)).isIn(200,401);assertThat(revoke.get(10,TimeUnit.SECONDS)).isTrue();
            assertThat(records.findById(registration.credentialId()).orElseThrow().getRevokedAt()).isNotNull();
            assertThat(jdbc.queryForObject("select count(*) from identity.refresh_tokens where user_id=? and revoked_at is null",Integer.class,user.getId())).isZero();
        } finally { pool.shutdownNow(); }
    }
}
