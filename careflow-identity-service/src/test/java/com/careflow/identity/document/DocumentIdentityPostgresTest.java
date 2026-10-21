package com.careflow.identity.document;

import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="DOCUMENT_TEST_JDBC",matches="jdbc:postgresql://127\\.0\\.0\\.1:15432/careflow_document_identity_test")
class DocumentIdentityPostgresTest {
    @TempDir static Path directory;
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    DocumentCipher cipher;
    DocumentIdentityService service;
    UUID owner,staff;
    final DocumentIdentityService.Fields fields=new DocumentIdentityService.Fields("000000000001","TEST PERSON","1990-01-02");
    @BeforeAll static void database() {
        var ds=new DriverManagerDataSource(System.getenv("DOCUMENT_TEST_JDBC"),System.getenv("RECOVERY_TEST_USER"),System.getenv("RECOVERY_TEST_PASSWORD"));
        Flyway.configure().dataSource(ds).schemas("identity").defaultSchema("identity").locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
    }
    @BeforeEach void fixture() throws Exception {
        jdbc.execute("TRUNCATE identity.document_checks");
        byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);
        Path file=directory.resolve("identity.key");Files.writeString(file,Base64.getEncoder().encodeToString(key));
        cipher=new DocumentCipher(file.toString());service=new DocumentIdentityService(jdbc,tx,cipher,new ObjectMapper());
        owner=user("PATIENT");staff=user("STAFF");
    }
    UUID user(String role) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO identity.users(id,username,email,password_hash,role,status,created_at,updated_at) VALUES (?,?,?,'fixture',?,'ACTIVE',now(),now())",id,"doc_"+id.toString().substring(0,8),id+"@example.test",role);
        return id;
    }
    DocumentIdentityService.Summary submit(UUID key) { return service.submit(owner,key,fields,true); }
    @Test void storesOnlyEncryptedFieldsAndNeverAutomaticallyConfirms() {
        var result=submit(UUID.randomUUID());assertThat(result.status()).isEqualTo("PENDING_REVIEW");
        assertThat(result.maskedDocumentNumber()).isEqualTo("********0001");
        var row=jdbc.queryForMap("SELECT * FROM identity.document_checks");
        assertThat(new String((byte[])row.get("sealed_fields"),java.nio.charset.StandardCharsets.UTF_8)).doesNotContain(fields.documentNumber(),fields.fullName());
        assertThat(service.detail(result.id()).fields()).isEqualTo(fields);
        assertThat(service.latest(UUID.randomUUID())).isNull();
    }
    @Test void concurrentReplaysCreateOneRecordAndChangedFieldsConflict() throws Exception {
        UUID key=UUID.randomUUID();try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->submit(key));var b=pool.submit(()->submit(key));
            assertThat(a.get(10,TimeUnit.SECONDS).id()).isEqualTo(b.get(10,TimeUnit.SECONDS).id());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.document_checks",Integer.class)).isEqualTo(1);
        assertThatThrownBy(()->service.submit(owner,key,new DocumentIdentityService.Fields("000000000002","TEST PERSON","1990-01-02"),true))
                .isInstanceOf(BusinessException.class).extracting("status").isEqualTo(409);
    }
    @Test void consentAndFieldValidationAreRequired() {
        assertThatThrownBy(()->service.submit(owner,UUID.randomUUID(),fields,false)).isInstanceOf(BusinessException.class);
        for(var bad:List.of(new DocumentIdentityService.Fields("123","TEST PERSON","1990-01-02"),
                new DocumentIdentityService.Fields("000000000001","<script>","1990-01-02"),
                new DocumentIdentityService.Fields("000000000001","TEST PERSON","1990-02-31")))
            assertThatThrownBy(()->service.submit(owner,UUID.randomUUID(),bad,true)).isInstanceOf(BusinessException.class);
    }
    @Test void pendingCannotBeReplacedAndReviewRequiresAllChecks() {
        var result=submit(UUID.randomUUID());
        assertThatThrownBy(()->submit(UUID.randomUUID())).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(409);
        assertThatThrownBy(()->service.review(staff,result.id(),true,true,false,true,"DESK-01")).isInstanceOf(BusinessException.class);
        assertThat(service.latest(owner).status()).isEqualTo("PENDING_REVIEW");
        assertThatThrownBy(()->service.review(owner,result.id(),true,true,true,true,"DESK-01")).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(403);
    }
    @Test void humanConfirmationErasesFieldsAndRecordsReviewer() {
        var result=submit(UUID.randomUUID());service.review(staff,result.id(),true,true,true,true,"DESK-01");
        var row=jdbc.queryForMap("SELECT * FROM identity.document_checks");
        assertThat(row.get("status")).isEqualTo("CONFIRMED");assertThat(row.get("sealed_fields")).isNull();assertThat(row.get("reviewed_by")).isEqualTo(staff);
        assertThat(service.detail(result.id()).fields()).isNull();
        assertThatThrownBy(()->service.review(staff,result.id(),true,true,true,true,"DESK-01")).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(409);
    }
    @Test void simultaneousReviewProducesOneDecision() throws Exception {
        UUID id=submit(UUID.randomUUID()).id();
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> call=()->{try { service.review(staff,id,true,true,true,true,"DESK-01");return true; } catch(BusinessException e) { return false; }};
            var a=pool.submit(call);var b=pool.submit(call);assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder(true,false);
        }
    }
    @Test void ownerCanWithdrawButOthersCannotAndFieldsAreErased() {
        UUID id=submit(UUID.randomUUID()).id();
        assertThatThrownBy(()->service.withdraw(UUID.randomUUID(),id)).isInstanceOf(BusinessException.class);
        assertThat(service.withdraw(owner,id).status()).isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject("SELECT sealed_fields FROM identity.document_checks",byte[].class)).isNull();
    }
    @Test void rejectedAndExpiredFieldsAreErasedAndExpiredCannotBeReviewed() {
        UUID id=submit(UUID.randomUUID()).id();service.review(staff,id,false,false,false,false,"DESK-01");
        assertThat(service.detail(id).fields()).isNull();assertThat(service.latest(owner).status()).isEqualTo("REJECTED");
        UUID next=submit(UUID.randomUUID()).id();jdbc.update("UPDATE identity.document_checks SET created_at=now()-interval '8 days' WHERE id=?",next);
        assertThat(service.detail(next).fields()).isNull();
        assertThatThrownBy(()->service.review(staff,next,true,true,true,true,"DESK-01")).isInstanceOf(BusinessException.class);
        service.cleanup();assertThat(jdbc.queryForObject("SELECT sealed_fields FROM identity.document_checks WHERE id=?",byte[].class,next)).isNull();
    }
    @Test void submissionsHaveDurableDailyLimit() {
        for(int i=0;i<3;i++) { UUID id=submit(UUID.randomUUID()).id();service.withdraw(owner,id); }
        assertThatThrownBy(()->submit(UUID.randomUUID())).isInstanceOf(BusinessException.class).extracting("status").isEqualTo(429);
    }
    @Test void cipherUsesRandomNonceBindsRowAndDetectsTampering() {
        UUID id=UUID.randomUUID();byte[] plain="private fixture".getBytes();byte[] a=cipher.seal(id,plain),b=cipher.seal(id,plain);
        assertThat(a).isNotEqualTo(b);assertThat(cipher.open(id,a)).isEqualTo(plain);
        assertThatThrownBy(()->cipher.open(UUID.randomUUID(),a)).isInstanceOf(BusinessException.class);
        a[a.length-1]^=1;assertThatThrownBy(()->cipher.open(id,a)).isInstanceOf(BusinessException.class);
        assertThat(new DocumentCipher("").ready()).isFalse();
    }
}
