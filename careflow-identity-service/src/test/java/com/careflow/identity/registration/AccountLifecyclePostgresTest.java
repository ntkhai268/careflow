package com.careflow.identity.registration;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.*;
import com.careflow.identity.dto.RegisterRequest;
import com.careflow.identity.recovery.*;
import com.careflow.identity.repository.*;
import com.careflow.identity.security.*;
import com.careflow.identity.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="PHASE2_TEST_JDBC",matches="jdbc:postgresql://127\\.0\\.0\\.1:15432/careflow_account_lifecycle_test")
@SpringBootTest(classes=AccountLifecyclePostgresTest.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
 properties={"eureka.client.enabled=false","spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false",
 "spring.flyway.enabled=true","spring.flyway.schemas=identity","spring.flyway.default-schema=identity"})
class AccountLifecyclePostgresTest {
    static final String WEBHOOK_SECRET="whsec_"+Base64.getEncoder().encodeToString(new byte[32]);
    static Path keyFile;
    @Configuration @EnableAutoConfiguration(exclude=com.careflow.common.security.ActiveSessionAutoConfiguration.class)
    @EntityScan(basePackageClasses=User.class) @EnableJpaRepositories(basePackages="com.careflow.identity.repository")
    @Import({EmailRegistrationService.class,RecoveryMailOutbox.class,MailDeliveryTracker.class,LoginMethodsService.class,
            RefreshTokenService.class,StaffPasskeyService.class,DeviceKeyMigrationService.class,SessionStepUp.class,BiometricDeviceService.class,AuthService.class})
    static class Config {
        @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(4); }
        @Bean RecordingSender sender() { return new RecordingSender(); }
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean JwtService jwt() {return mock(JwtService.class);}
    }
    record Message(String kind,String code,String provider) { }
    static class RecordingSender implements RecoveryEmailSender {
        final Map<UUID,Message> messages=new ConcurrentHashMap<>();
        public boolean configured(){return true;}
        public void sendCode(String email,String code,UUID id){sendTracked("CODE",email,code,id);}
        public void sendChanged(String email,UUID id){sendTracked("CHANGED",email,null,id);}
        public String sendTracked(String kind,String email,String code,UUID id){
            String provider=UUID.randomUUID().toString();messages.put(id,new Message(kind,code,provider));return provider;
        }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) throws Exception {
        if(keyFile==null){keyFile=Files.createTempFile("careflow-phase2-test-key-",".key");byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);Files.writeString(keyFile,Base64.getEncoder().encodeToString(bytes));keyFile.toFile().deleteOnExit();}
        properties.add("recovery.outbox-key-file",()->keyFile.toString());
        properties.add("resend.webhook-secret",()->WEBHOOK_SECRET);
        properties.add("spring.datasource.url",()->System.getenv("PHASE2_TEST_JDBC"));
        properties.add("spring.datasource.username",()->System.getenv("RECOVERY_TEST_USER"));
        properties.add("spring.datasource.password",()->System.getenv("RECOVERY_TEST_PASSWORD"));
    }
    @Autowired EmailRegistrationService registration;
    @Autowired RecoveryMailOutbox outbox;
    @Autowired RecordingSender sender;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired RefreshTokenService sessions;
    @Autowired BiometricDeviceRepository devices;
    @Autowired DeviceKeyMigrationService migration;
    @Autowired BiometricDeviceService biometric;
    @Autowired StaffPasskeyService staff;
    @Autowired SessionStepUp stepUp;
    @Autowired AuthService auth;
    @Autowired MailDeliveryTracker tracker;
    @Autowired JwtService jwt;
    final String oldJkt="a".repeat(43),newJkt="b".repeat(43);
    RegisterRequest request;

    @BeforeEach void fixture() {
        // The annotation accepts exactly this disposable loopback database, never a runtime/remote DB.
        jdbc.execute("TRUNCATE identity.login_challenges,identity.staff_passkey_grants,identity.signup_challenges,identity.recovery_mail_outbox,identity.mail_delivery_events");
        sender.messages.clear();
        String name="signup_"+UUID.randomUUID().toString().substring(0,8);
        request=new RegisterRequest(name,name+"@example.test","TestPassword123!");
        when(jwt.issue(any(),any())).thenReturn("fixture-access-token");when(jwt.expirationSeconds()).thenReturn(900L);
    }
    User user(UserRole role) {
        var user=new User();user.setUsername("u_"+UUID.randomUUID().toString().substring(0,8));user.setEmail(UUID.randomUUID()+"@example.test");
        user.setPasswordHash(passwords.encode("TestPassword123!"));user.setRole(role);return users.saveAndFlush(user);
    }
    String code(UUID id){outbox.deliver();return sender.messages.get(id).code();}
    void fixturePasskey(User user){jdbc.update("INSERT INTO identity.passkeys(credential_id,user_id,public_key_cose,signature_count) VALUES (?,?,?,0)",Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)),user.getId(),"AQ");}

    @Test void signupCreatesNoUsableAccountUntilEmailCodeIsVerified() {
        var pending=registration.request(request,oldJkt);
        assertThat(users.existsByUsernameIgnoreCase(request.username())).isFalse();
        String code=code(pending.requestId());registration.verify(pending.requestId(),code,oldJkt);
        var created=users.findByEmailIgnoreCase(request.email()).orElseThrow();
        assertThat(created.getRole()).isEqualTo(UserRole.PATIENT);assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(jdbc.queryForObject("SELECT email_verified_at FROM identity.users WHERE id=?",java.sql.Timestamp.class,created.getId())).isNotNull();
        assertThat(passwords.matches(request.password(),created.getPasswordHash())).isTrue();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM identity.signup_challenges WHERE request_id=?",String.class,pending.requestId())).isNull();
        assertThatThrownBy(()->registration.verify(pending.requestId(),code,oldJkt)).isInstanceOf(BusinessException.class);
    }
    @Test void wrongCodeHasFiveDurableAttemptsAndCannotBeReplayedFromAnotherDevice() {
        var pending=registration.request(request,oldJkt);String code=code(pending.requestId());
        assertThatThrownBy(()->registration.verify(pending.requestId(),code,newJkt)).isInstanceOf(BusinessException.class);
        String wrong=code.equals("000000")?"999999":"000000";
        for(int i=0;i<5;i++)assertThatThrownBy(()->registration.verify(pending.requestId(),wrong,oldJkt)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT failed_attempts FROM identity.signup_challenges WHERE request_id=?",Integer.class,pending.requestId())).isEqualTo(5);
        assertThatThrownBy(()->registration.verify(pending.requestId(),code,oldJkt)).isInstanceOf(BusinessException.class);
        assertThat(users.existsByEmailIgnoreCase(request.email())).isFalse();
    }
    @Test void registrationCooldownAndExpiryAreServerEnforced() {
        var pending=registration.request(request,oldJkt);String code=code(pending.requestId());
        assertThatThrownBy(()->registration.request(request,oldJkt)).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(429);
        jdbc.update("UPDATE identity.signup_challenges SET expires_at=now()-interval '1 second' WHERE request_id=?",pending.requestId());
        assertThatThrownBy(()->registration.verify(pending.requestId(),code,oldJkt)).isInstanceOf(BusinessException.class);
    }
    @Test void resendExpiresOldQueuedEmailAndOldCode() {
        var first=registration.request(request,oldJkt);
        jdbc.update("UPDATE identity.signup_challenges SET last_sent_at=now()-interval '61 seconds'");
        var second=registration.request(request,oldJkt);
        outbox.deliver();assertThat(sender.messages).doesNotContainKey(first.requestId());
        String code=code(second.requestId());
        assertThatThrownBy(()->registration.verify(first.requestId(),code,oldJkt)).isInstanceOf(BusinessException.class);
        registration.verify(second.requestId(),code,oldJkt);
    }
    @Test void concurrentVerificationCreatesExactlyOneAccount() throws Exception {
        var pending=registration.request(request,oldJkt);String code=code(pending.requestId());
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> call=()->{try{registration.verify(pending.requestId(),code,oldJkt);return true;}catch(BusinessException ex){return false;}};
            var a=pool.submit(call);var b=pool.submit(call);
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.users WHERE lower(email)=?",Integer.class,request.email())).isEqualTo(1);
    }
    @Test void alreadyRegisteredEmailGetsSamePublicResponseButNoNewCode() {
        var known=user(UserRole.PATIENT);
        var first=registration.request(request,oldJkt);
        var duplicate=registration.request(new RegisterRequest(request.username()+"x",known.getEmail(),request.password()),oldJkt);
        assertThat(first.message()).isEqualTo(duplicate.message());
        outbox.deliver();outbox.deliver();assertThat(sender.messages).doesNotContainKey(duplicate.requestId());
    }
    @Test void signupAndResetCodesCannotShareTheirPurpose() {
        var pending=registration.request(request,oldJkt);code(pending.requestId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.password_reset_codes WHERE challenge_id=?",Integer.class,pending.requestId())).isZero();
        assertThat(jdbc.queryForObject("SELECT kind FROM identity.recovery_mail_outbox WHERE challenge_id=?",String.class,pending.requestId())).isEqualTo("SIGNUP");
    }
    @Test void staffRecoveryRequiresBothEmailAndSeparatelyDeliveredActivation() throws Exception {
        var admin=user(UserRole.ADMIN);var target=user(UserRole.DOCTOR);fixturePasskey(target);var old=sessions.issuePasskey(target,oldJkt);
        var grant=staff.issue(admin.getId(),target.getId(),true,"TEST: verified in person");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.passkeys WHERE user_id=?",Integer.class,target.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM identity.refresh_tokens WHERE id=?",Boolean.class,old.sessionId())).isTrue();
        String mail=code(grant.grantId()).split("\\.")[1];
        assertThatThrownBy(()->staff.enrollment(grant.grantId(),mail,"x".repeat(43),newJkt)).isInstanceOf(BusinessException.class);
        var options=staff.enrollment(grant.grantId(),mail,grant.activationCode(),newJkt);
        assertThat(options).containsKeys("requestId","options");
        assertThat(jdbc.queryForObject("SELECT staff_grant_id FROM identity.login_challenges WHERE id=?",UUID.class,options.get("requestId"))).isEqualTo(grant.grantId());
        assertThat(jdbc.queryForMap("SELECT token_hash,activation_hash FROM identity.staff_passkey_grants WHERE id=?",grant.grantId()).values().toString())
                .doesNotContain(mail,grant.activationCode());
    }
    @Test void invalidStaffGrantStopsAfterFiveAttempts() {
        var grant=staff.issue(user(UserRole.ADMIN).getId(),user(UserRole.STAFF).getId(),false,"TEST: identity verified");
        for(int i=0;i<5;i++)assertThatThrownBy(()->staff.enrollment(grant.grantId(),"x".repeat(43),"y".repeat(43),newJkt)).isInstanceOf(BusinessException.class);
        String mail=code(grant.grantId()).split("\\.")[1];
        assertThatThrownBy(()->staff.enrollment(grant.grantId(),mail,grant.activationCode(),newJkt)).isInstanceOf(BusinessException.class);
    }
    @Test void lastStaffPasskeyCannotBeRemoved() {
        var target=user(UserRole.DOCTOR);fixturePasskey(target);
        String id=jdbc.queryForObject("SELECT credential_id FROM identity.passkeys WHERE user_id=?",String.class,target.getId());
        assertThatThrownBy(()->staff.remove(target.getId(),id)).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(409);
    }
    @Test void refreshPreservesAuthenticationAgeAndDoesNotSatisfyFreshPasskeyStepUp() {
        var target=user(UserRole.ADMIN);var first=sessions.issuePasskey(target,oldJkt);
        jdbc.update("UPDATE identity.refresh_tokens SET authenticated_at=now()-interval '10 minutes' WHERE id=?",first.sessionId());
        var next=sessions.rotate(first.value(),oldJkt);
        var auth=new UsernamePasswordAuthenticationToken(target.getId().toString(),null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        auth.setDetails(new JwtService.AuthenticatedUser(target.getId(),target.getRole(),next.sessionId(),Instant.now().plusSeconds(900),oldJkt));
        assertThatThrownBy(()->stepUp.requireFreshPasskey(auth,true)).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(403);
    }
    @Test void backgroundRefreshCannotExtendIdleSession() {
        var target=user(UserRole.DOCTOR);var session=sessions.issuePasskey(target,oldJkt);
        jdbc.update("UPDATE identity.refresh_tokens SET last_interactive_at=now()-interval '16 minutes' WHERE id=?",session.sessionId());
        assertThatThrownBy(()->sessions.rotate(session.value(),oldJkt)).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(401);
    }
    @Test void hardwareMigrationKeepsEnrollmentSecretAndOtherDeviceBinding() {
        var target=user(UserRole.PATIENT);var registration=biometric.enroll(target.getId(),"TestPassword123!",oldJkt);
        var old=sessions.replaceForDevice(target,oldJkt);var other=sessions.issue(target,"c".repeat(43));
        var principal=new JwtService.AuthenticatedUser(target.getId(),target.getRole(),old.sessionId(),Instant.now().plusSeconds(900),oldJkt);
        var result=migration.rotate(principal,newJkt,null,registration.credentialId(),registration.deviceToken());
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(devices.findById(registration.credentialId()).orElseThrow().getDpopJkt()).isEqualTo(newJkt);
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NULL FROM identity.refresh_tokens WHERE id=?",Boolean.class,other.sessionId())).isTrue();
        assertThat(biometric.login(registration.credentialId(),registration.deviceToken(),newJkt)).isNotNull();
        assertThatThrownBy(()->biometric.login(registration.credentialId(),registration.deviceToken(),oldJkt)).isInstanceOf(BusinessException.class);
    }
    @Test void revokedSessionCannotMigrateKeysEvenWithCorrectPassword() {
        var target=user(UserRole.PATIENT);var old=sessions.issue(target,oldJkt);sessions.revokeAll(target);
        var principal=new JwtService.AuthenticatedUser(target.getId(),target.getRole(),old.sessionId(),Instant.now().plusSeconds(900),oldJkt);
        assertThatThrownBy(()->migration.rotate(principal,newJkt,"TestPassword123!",null,null)).isInstanceOf(BusinessException.class);
    }
    byte[] payload(String provider,String type) {return ("{\"type\":\""+type+"\",\"created_at\":\""+Instant.now()+"\",\"data\":{\"email_id\":\""+provider+"\"}}").getBytes(StandardCharsets.UTF_8);}
    String signature(String id,String timestamp,byte[] body) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(new byte[32],"HmacSHA256"));mac.update((id+"."+timestamp+".").getBytes(StandardCharsets.UTF_8));return "v1,"+Base64.getEncoder().encodeToString(mac.doFinal(body));
    }
    @Test void signedDeliveryWebhookIsIdempotentAndDoesNotDowngradeBounce() throws Exception {
        var pending=registration.request(request,oldJkt);code(pending.requestId());String provider=sender.messages.get(pending.requestId()).provider();
        String time=Long.toString(Instant.now().getEpochSecond());byte[] body=payload(provider,"email.bounced");
        tracker.accept("msg_test_bounce",time,signature("msg_test_bounce",time,body),body);
        tracker.accept("msg_test_bounce",time,signature("msg_test_bounce",time,body),body);
        byte[] delivered=payload(provider,"email.delivered");tracker.accept("msg_test_delivered",time,signature("msg_test_delivered",time,delivered),delivered);
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM identity.recovery_mail_outbox WHERE challenge_id=?",String.class,pending.requestId())).isEqualTo("BOUNCED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.mail_delivery_events",Integer.class)).isEqualTo(2);
    }
    @Test void webhookTamperingAndOldSignatureCannotModifyDelivery() throws Exception {
        byte[] body=payload(UUID.randomUUID().toString(),"email.delivered");String time=Long.toString(Instant.now().getEpochSecond());
        assertThatThrownBy(()->tracker.accept("msg_tamper",time,signature("msg_tamper",time,"different".getBytes()),body)).isInstanceOf(BusinessException.class);
        String old=Long.toString(Instant.now().minusSeconds(600).getEpochSecond());
        assertThatThrownBy(()->tracker.accept("msg_old",old,signature("msg_old",old,body),body)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.mail_delivery_events",Integer.class)).isZero();
    }
    @Test void administrativeStatusOrRoleChangeInvalidatesPendingGrants() {
        var administrator=user(UserRole.ADMIN);var target=user(UserRole.DOCTOR);
        var first=staff.issue(administrator.getId(),target.getId(),false,"TEST: identity verified");
        auth.updateStatus(target.getId(),UserStatus.DISABLED);
        assertThat(jdbc.queryForObject("SELECT used_at IS NOT NULL FROM identity.staff_passkey_grants WHERE id=?",Boolean.class,first.grantId())).isTrue();
        auth.updateStatus(target.getId(),UserStatus.ACTIVE);
        var second=staff.issue(administrator.getId(),target.getId(),false,"TEST: identity rechecked");
        auth.updateRole(target.getId(),UserRole.STAFF);
        assertThat(jdbc.queryForObject("SELECT used_at IS NOT NULL FROM identity.staff_passkey_grants WHERE id=?",Boolean.class,second.grantId())).isTrue();
    }

    @Test void patientPasskeyCannotBypassTrustedStaffOnboardingAfterRolePromotion() {
        var target=user(UserRole.PATIENT);fixturePasskey(target);
        UUID challenge=UUID.randomUUID();
        jdbc.update("INSERT INTO identity.login_challenges(id,user_id,kind,request_json,dpop_jkt,expires_at) VALUES (?,?,'register','{}',?,now()+interval '2 minutes')",challenge,target.getId(),oldJkt);
        auth.updateRole(target.getId(),UserRole.STAFF);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.passkeys WHERE user_id=?",Integer.class,target.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.login_challenges WHERE user_id=?",Integer.class,target.getId())).isZero();
        assertThat(users.findById(target.getId()).orElseThrow().getRole()).isEqualTo(UserRole.STAFF);
    }

}
