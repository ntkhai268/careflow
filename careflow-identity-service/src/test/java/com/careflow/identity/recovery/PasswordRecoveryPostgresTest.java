package com.careflow.identity.recovery;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.*;
import com.careflow.identity.repository.*;
import com.careflow.identity.service.RefreshTokenService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in, isolated PostgreSQL only: never create/drop tables in the runtime database. */
@EnabledIfEnvironmentVariable(named="RECOVERY_TEST_JDBC",matches="jdbc:postgresql://127\\.0\\.0\\.1:15432/careflow_email_recovery_test")
@SpringBootTest(classes=PasswordRecoveryPostgresTest.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
 properties={"eureka.client.enabled=false","spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false",
 "spring.flyway.enabled=true","spring.flyway.schemas=identity","spring.flyway.default-schema=identity",
 "logging.level.org.hibernate.SQL=OFF"})
class PasswordRecoveryPostgresTest {
    @Configuration @EnableAutoConfiguration(exclude=com.careflow.common.security.ActiveSessionAutoConfiguration.class)
    @EntityScan(basePackageClasses={User.class,PasswordResetCode.class})
    @EnableJpaRepositories(basePackages={"com.careflow.identity.repository","com.careflow.identity.recovery"})
    @Import({PasswordRecoveryService.class,RefreshTokenService.class,RecoveryMailListener.class,RecoveryMailConfiguration.class,RecoveryMailOutbox.class})
    static class Config {
        @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(4); }
        @Bean RecordingSender sender() { return new RecordingSender(); }
    }
    static class RecordingSender implements RecoveryEmailSender {
        final Map<UUID,String> codes=new ConcurrentHashMap<>();
        public boolean configured() { return true; }
        public void sendCode(String email,String code,UUID id) { codes.put(id,code); }
        public void sendChanged(String email,UUID id) {}
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("recovery.outbox-key-file",PasswordRecoveryPostgresTest::outboxKeyFile);
        properties.add("spring.datasource.url",()->System.getenv("RECOVERY_TEST_JDBC"));
        properties.add("spring.datasource.username",()->System.getenv("RECOVERY_TEST_USER"));
        properties.add("spring.datasource.password",()->System.getenv("RECOVERY_TEST_PASSWORD"));
    }
    static String outboxKeyFile() {
        try {
            var path=java.nio.file.Files.createTempFile("careflow-test-outbox-",".key");
            byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);
            java.nio.file.Files.writeString(path,Base64.getEncoder().encodeToString(key));path.toFile().deleteOnExit();return path.toString();
        } catch(Exception failure) { throw new IllegalStateException(failure); }
    }
    @Autowired PasswordRecoveryService recovery;
    @Autowired RefreshTokenService sessions;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired RecordingSender sender;
    @Autowired PasswordEncoder passwords;
    User user; String email;final String jkt="a".repeat(43);
    @BeforeEach void account() {
        String name="recovery_"+UUID.randomUUID().toString().substring(0,8);email=name+"@example.test";
        user=new User();user.setId(UUID.randomUUID());user.setUsername(name);user.setEmail(email);
        user.setPasswordHash(passwords.encode("OldPassword123!"));user.setStatus(UserStatus.ACTIVE);user.setRole(UserRole.PATIENT);
        user=users.saveAndFlush(user);
    }
    String awaitCode(UUID id) throws Exception {
        for(int i=0;i<100;i++) {
            var states=jdbc.queryForList("select delivery_state from identity.password_reset_codes where challenge_id=?",String.class,id);
            if(sender.codes.containsKey(id) && states.equals(List.of("SENT"))) return sender.codes.get(id);
            Thread.sleep(30);
        }
        throw new AssertionError("After-commit test delivery did not finish");
    }
    void allowResend() { jdbc.update("update identity.password_reset_codes set last_sent_at=now()-interval '61 seconds' where user_id=?",user.getId()); }
    @Test void attemptsCommitOnFailureAndResendInvalidatesOldCode() throws Exception {
        var first=recovery.request(email,jkt);String old=awaitCode(first.requestId());
        String wrong=old.equals("000000")?"999999":"000000";
        for(int i=0;i<5;i++) assertThatThrownBy(()->recovery.verify(first.requestId(),wrong,jkt)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select failed_attempts from identity.password_reset_codes where user_id=?",Integer.class,user.getId())).isEqualTo(5);
        assertThatThrownBy(()->recovery.verify(first.requestId(),old,jkt)).isInstanceOf(BusinessException.class);
        allowResend();var next=recovery.request(email,jkt);String code=awaitCode(next.requestId());
        assertThatThrownBy(()->recovery.verify(first.requestId(),old,jkt)).isInstanceOf(BusinessException.class);
        var grant=recovery.verify(next.requestId(),code,jkt);
        recovery.confirm(next.requestId(),grant.resetToken(),"NewPassword123!",jkt);
        assertThat(passwords.matches("NewPassword123!",users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
    }
    @Test void concurrentConfirmationHasExactlyOneWinnerAndRevokesEverySession() throws Exception {
        sessions.issue(user,jkt);sessions.issue(user,"b".repeat(43));
        var request=recovery.request(email,jkt);String code=awaitCode(request.requestId());
        assertThatThrownBy(()->recovery.confirm(request.requestId(),"x".repeat(43),"NewPassword123!",jkt)).isInstanceOf(BusinessException.class);
        var grant=recovery.verify(request.requestId(),code,jkt);
        assertThatThrownBy(()->recovery.verify(request.requestId(),code,jkt)).isInstanceOf(BusinessException.class);
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<Integer> confirm=()->{start.await();try { recovery.confirm(request.requestId(),grant.resetToken(),"NewPassword123!",jkt);return 200; }
            catch(BusinessException error) { return error.getStatus(); }};
        try {
            var a=pool.submit(confirm);var b=pool.submit(confirm);start.countDown();
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,400);
        } finally { pool.shutdownNow(); }
        assertThat(jdbc.queryForObject("select count(*) from identity.refresh_tokens where user_id=? and revoked_at is null",Integer.class,user.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from identity.security_events where user_id=? and event_type='EMAIL_PASSWORD_RESET'",Integer.class,user.getId())).isEqualTo(1);
    }
    @Test void concurrentCodeVerificationIssuesExactlyOnePermission() throws Exception {
        var request=recovery.request(email,jkt);String code=awaitCode(request.requestId());
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<Object> verify=()->{start.await();try { return recovery.verify(request.requestId(),code,jkt); }
            catch(BusinessException error) { return error.getStatus(); }};
        try {
            var a=pool.submit(verify);var b=pool.submit(verify);start.countDown();
            var results=List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            assertThat(results.stream().filter(v->v instanceof PasswordRecoveryService.VerifyResult).count()).isEqualTo(1);
            assertThat(results).contains(400);
            assertThat(passwords.matches("OldPassword123!",users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
            var grant=(PasswordRecoveryService.VerifyResult)results.stream().filter(v->v instanceof PasswordRecoveryService.VerifyResult).findFirst().orElseThrow();
            recovery.confirm(request.requestId(),grant.resetToken(),"NewPassword123!",jkt);
        } finally { pool.shutdownNow(); }
    }
    @Test void concurrentRequestsShareTheSamePersistedCooldown() throws Exception {
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<Integer> request=()->{start.await();try { recovery.request(email,jkt);return 202; } catch(BusinessException error) { return error.getStatus(); }};
        try {
            var a=pool.submit(request);var b=pool.submit(request);start.countDown();
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(202,429);
        } finally { pool.shutdownNow(); }
    }
    @Test void unknownMailboxAlsoPersistsFailedVerificationWithoutSending() {
        String unknown="unknown-"+UUID.randomUUID()+"@example.invalid";
        var request=recovery.request(unknown,jkt);
        String hash=jdbc.queryForObject("select code_hash from identity.password_reset_codes where challenge_id=?",String.class,request.requestId());
        String wrong=passwords.matches("000000",hash)?"999999":"000000";
        assertThatThrownBy(()->recovery.verify(request.requestId(),wrong,jkt)).isInstanceOf(BusinessException.class);
        Integer attempts=jdbc.queryForObject("select failed_attempts from identity.password_reset_codes where challenge_id=?",Integer.class,request.requestId());
        assertThat(attempts).isEqualTo(1);
        assertThat(sender.codes).doesNotContainKey(request.requestId());
        assertThat(users.findByEmailIgnoreCase(unknown)).isEmpty();
    }
    @Test void failedTransactionCannotPartiallyReplacePasswordOrConsumeCode() throws Exception {
        var request=recovery.request(email,jkt);String code=awaitCode(request.requestId());
        var grant=recovery.verify(request.requestId(),code,jkt);
        jdbc.execute("alter table identity.security_events add constraint recovery_test_fail check (event_type <> 'EMAIL_PASSWORD_RESET') not valid");
        try {
            assertThatThrownBy(()->recovery.confirm(request.requestId(),grant.resetToken(),"NewPassword123!",jkt)).isInstanceOf(RuntimeException.class);
            assertThat(passwords.matches("OldPassword123!",users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
            assertThat(jdbc.queryForObject("select used_at is null from identity.password_reset_codes where user_id=?",Boolean.class,user.getId())).isTrue();
        } finally { jdbc.execute("alter table identity.security_events drop constraint recovery_test_fail"); }
        recovery.confirm(request.requestId(),grant.resetToken(),"NewPassword123!",jkt);
    }
    @Test void passwordResetInvalidatesEveryOutstandingStaffEnrollmentGrant() throws Exception {
        user.setRole(UserRole.STAFF);users.saveAndFlush(user);
        UUID pending=UUID.randomUUID();
        jdbc.update("INSERT INTO identity.staff_passkey_grants(id,user_id,token_hash,activation_hash,kind,identity_check_reference,expires_at) VALUES (?,?,?,?,'ENROLL','TEST: checked identity',now()+interval '15 minutes')",pending,user.getId(),UUID.randomUUID().toString().replace("-","").repeat(2),"b".repeat(64));
        var request=recovery.request(email,jkt);
        var grant=recovery.verify(request.requestId(),awaitCode(request.requestId()),jkt);
        assertThat(jdbc.queryForObject("SELECT used_at IS NULL FROM identity.staff_passkey_grants WHERE id=?",Boolean.class,pending)).isTrue();
        recovery.confirm(request.requestId(),grant.resetToken(),"NewPassword123!",jkt);
        assertThat(jdbc.queryForObject("SELECT used_at IS NOT NULL FROM identity.staff_passkey_grants WHERE id=?",Boolean.class,pending)).isTrue();
    }

}
