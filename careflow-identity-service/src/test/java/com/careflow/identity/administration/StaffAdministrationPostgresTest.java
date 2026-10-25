package com.careflow.identity.administration;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.domain.*;
import com.careflow.identity.dto.LoginRequest;
import com.careflow.identity.repository.*;
import com.careflow.identity.security.JwtService;
import com.careflow.identity.service.*;
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
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="R1_TEST_JDBC",matches="jdbc:postgresql://127\\.0\\.0\\.1:15432/careflow_r1_administration_test")
@SpringBootTest(classes=StaffAdministrationPostgresTest.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
        properties={"eureka.client.enabled=false","spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false",
                "spring.flyway.enabled=true","spring.flyway.schemas=identity","spring.flyway.default-schema=identity"})
class StaffAdministrationPostgresTest {
    @Configuration
    @EnableAutoConfiguration(exclude=com.careflow.common.security.ActiveSessionAutoConfiguration.class)
    @EntityScan(basePackageClasses=User.class)
    @EnableJpaRepositories(basePackages="com.careflow.identity.repository")
    @Import({StaffAccountService.class,AuthService.class,RefreshTokenService.class})
    static class Config {
        @Bean PasswordEncoder passwords(){return new BCryptPasswordEncoder(4);}
        @Bean JwtService jwt(){return mock(JwtService.class);}
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties){
        properties.add("spring.datasource.url",()->System.getenv("R1_TEST_JDBC"));
        properties.add("spring.datasource.username",()->System.getenv("RECOVERY_TEST_USER"));
        properties.add("spring.datasource.password",()->System.getenv("RECOVERY_TEST_PASSWORD"));
    }
    @Autowired StaffAccountService staff;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository tokens;
    @Autowired RefreshTokenService sessions;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthService auth;
    JwtService.AuthenticatedUser administrator;
    final String binding="a".repeat(43);

    @BeforeEach void fixture(){
        // The annotation permits only this disposable owned test DB, never a runtime/remote DB.
        jdbc.execute("TRUNCATE identity.users CASCADE");
        administrator=administrator("operator");
    }
    User user(String username,UserRole role){
        User user=new User();user.setUsername(username);user.setEmail(username+"@example.test");user.setRole(role);
        user.setPasswordHash(passwords.encode("FixturePassword123!"));return users.saveAndFlush(user);
    }
    JwtService.AuthenticatedUser administrator(String name){
        User user=user(name,UserRole.ADMIN);
        jdbc.update("INSERT INTO identity.passkeys(credential_id,user_id,public_key_cose,signature_count) VALUES (?,?,?,0)",name,user.getId(),"AQ");
        var session=sessions.issuePasskey(user,binding);
        return new JwtService.AuthenticatedUser(user.getId(),UserRole.ADMIN,session.sessionId(),Instant.now().plusSeconds(300),binding);
    }
    StaffAccountService.Create request(){return new StaffAccountService.Create("New.Staff","New.Staff@Example.Test","TEST STAFF",UserRole.STAFF);}
    UUID create(){return (UUID)staff.create(administrator,UUID.randomUUID(),request()).get("id");}

    @Test void createsRealNormalizedAccountWithoutTemporaryPasswordAndAuditsActor(){
        UUID id=create();var result=staff.find(id);
        assertThat(result).containsEntry("username","new.staff").containsEntry("email","new.staff@example.test").containsEntry("displayName","TEST STAFF").containsEntry("role","STAFF");
        assertThat(((Number)result.get("passkeyCount")).longValue()).isZero();
        assertThat(jdbc.queryForObject("SELECT actor_id FROM identity.staff_account_audit WHERE user_id=?",UUID.class,id)).isEqualTo(administrator.userId());
        assertThat(users.isProvisionedStaff(id)).isTrue();
        assertThat(passwords.matches("FixturePassword123!",users.findById(id).orElseThrow().getPasswordHash())).isFalse();
    }
    @Test void replayReturnsSameAccountAndChangedPayloadConflicts(){
        UUID key=UUID.randomUUID();var first=staff.create(administrator,key,request());
        assertThat(staff.create(administrator,key,request()).get("id")).isEqualTo(first.get("id"));
        assertThatThrownBy(()->staff.create(administrator,key,new StaffAccountService.Create("other","other@example.test","OTHER",UserRole.DOCTOR)))
                .isInstanceOf(BusinessException.class).extracting("status").isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.staff_account_provisioning",Integer.class)).isEqualTo(1);
    }
    @Test void concurrentReplayCreatesExactlyOneAccount() throws Exception {
        UUID key=UUID.randomUUID();ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->staff.create(administrator,key,request()).get("id"));
            var b=pool.submit(()->staff.create(administrator,key,request()).get("id"));
            assertThat(a.get(10,TimeUnit.SECONDS)).isEqualTo(b.get(10,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.staff_account_provisioning",Integer.class)).isEqualTo(1);
    }
    @Test void duplicateMailboxDoesNotLeaveHalfAnAccountOrProvisioningRecord(){
        create();long count=users.count();
        assertThatThrownBy(()->staff.create(administrator,UUID.randomUUID(),request())).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(409);
        assertThat(users.count()).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.staff_account_provisioning",Integer.class)).isEqualTo(1);
    }
    @Test void patientRoleCannotBeProvisionedAsStaff(){
        assertThatThrownBy(()->staff.create(administrator,UUID.randomUUID(),new StaffAccountService.Create("patient","p@example.test","PATIENT",UserRole.PATIENT)))
                .isInstanceOf(BusinessException.class).extracting("status").isEqualTo(400);
        assertThat(users.count()).isEqualTo(1);
    }
    @Test void listUsesIdentityFieldsFiltersAndEscapesWildcards(){
        UUID id=create();user("patient",UserRole.PATIENT);
        assertThat(staff.list("TEST STAFF",UserRole.STAFF,UserStatus.ACTIVE,0).items()).extracting(row->row.get("id")).containsExactly(id);
        assertThat(staff.list("%",null,null,0).total()).isZero();
        assertThat(staff.list("",null,null,0).total()).isEqualTo(2);
        assertThatThrownBy(()->staff.list("",UserRole.PATIENT,null,0)).isInstanceOf(BusinessException.class);
    }
    @Test void passwordSessionCannotAdministerEvenWithAdminRole(){
        User user=users.findById(administrator.userId()).orElseThrow();var passwordSession=sessions.issue(user,binding);
        var actor=new JwtService.AuthenticatedUser(user.getId(),UserRole.ADMIN,passwordSession.sessionId(),Instant.now().plusSeconds(300),binding);
        assertThatThrownBy(()->staff.create(actor,UUID.randomUUID(),request())).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(403);
    }
    @Test void expiredOrRevokedStepUpIsCheckedInsideTheTransaction(){
        jdbc.update("UPDATE identity.refresh_tokens SET authenticated_at=now()-interval '6 minutes' WHERE id=?",administrator.sessionId());
        assertThatThrownBy(()->staff.create(administrator,UUID.randomUUID(),request())).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(403);
        jdbc.update("UPDATE identity.refresh_tokens SET authenticated_at=now(),revoked_at=now() WHERE id=?",administrator.sessionId());
        assertThatThrownBy(()->staff.create(administrator,UUID.randomUUID(),request())).isInstanceOf(BusinessException.class);
    }
    @Test void changedActorRoleCannotUseStaleAuthenticatedSnapshot(){
        jdbc.update("UPDATE identity.users SET role='STAFF' WHERE id=?",administrator.userId());
        assertThatThrownBy(()->staff.create(administrator,UUID.randomUUID(),request())).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(403);
    }
    @Test void lockRevokesRealSessionAndUnlockDoesNotRestoreIt(){
        User target=user("staff",UserRole.STAFF);var session=sessions.issuePasskey(target,binding);
        staff.status(administrator,target.getId(),UserStatus.LOCKED);
        assertThat(tokens.findById(session.sessionId()).orElseThrow().getRevokedAt()).isNotNull();
        staff.status(administrator,target.getId(),UserStatus.ACTIVE);
        assertThat(tokens.findById(session.sessionId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.staff_account_audit WHERE user_id=? AND action='STATUS_CHANGED'",Integer.class,target.getId())).isEqualTo(2);
    }
    @Test void cannotLockOrDemoteTheCurrentAdministrator(){
        assertThatThrownBy(()->staff.status(administrator,administrator.userId(),UserStatus.LOCKED)).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(409);
        assertThatThrownBy(()->staff.role(administrator,administrator.userId(),UserRole.STAFF)).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(409);
    }
    @Test void concurrentAdministratorsCannotLockEachOtherAndLeaveNoAdministrator() throws Exception {
        var other=administrator("backup");ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> a=()->{try{staff.status(administrator,other.userId(),UserStatus.LOCKED);return 200;}catch(BusinessException expected){return expected.getStatus();}};
            Callable<Integer> b=()->{try{staff.status(other,administrator.userId(),UserStatus.LOCKED);return 200;}catch(BusinessException expected){return expected.getStatus();}};
            var first=pool.submit(a);var second=pool.submit(b);
            assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,403);
        } finally {pool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.users WHERE role='ADMIN' AND status='ACTIVE'",Integer.class)).isEqualTo(1);
    }
    @Test void roleChangeRevokesSessionsAndWritesActualIdentityRole(){
        User target=user("staff",UserRole.STAFF);var session=sessions.issuePasskey(target,binding);
        staff.role(administrator,target.getId(),UserRole.LAB_TECHNICIAN);
        assertThat(users.findById(target.getId()).orElseThrow().getRole()).isEqualTo(UserRole.LAB_TECHNICIAN);
        assertThat(tokens.findById(session.sessionId()).orElseThrow().getRevokedAt()).isNotNull();
    }
    @Test void newlyProvisionedStaffCannotUsePasswordEvenInLegacyDemoMode(){
        UUID id=create();User target=users.findById(id).orElseThrow();target.setPasswordHash(passwords.encode("FixturePassword123!"));users.saveAndFlush(target);
        assertThatThrownBy(()->auth.login(new LoginRequest("new.staff","FixturePassword123!"),binding)).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(403);
    }
}
