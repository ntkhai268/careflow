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
import static org.assertj.core.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="R2_DB_TESTS",matches="1")
class ConfirmedDocumentMatchPostgresTest {
    @TempDir Path directory;
    DocumentIdentityService service;JdbcTemplate jdbc;UUID owner=UUID.randomUUID(),staff=UUID.randomUUID();
    final DocumentIdentityService.Fields fields=new DocumentIdentityService.Fields("000000000001","TEST PERSON","1990-01-01");
    @BeforeEach void setup()throws Exception{
        var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:15432/careflow_r2_identity_test",System.getenv("R2_DB_USER"),System.getenv("R2_DB_PASSWORD"));Flyway.configure().dataSource(ds).schemas("identity").defaultSchema("identity").locations("classpath:db/migration").load().migrate();jdbc=new JdbcTemplate(ds);
        for(var entry:Map.of(owner,"PATIENT",staff,"STAFF").entrySet())jdbc.update("INSERT INTO identity.users(id,username,email,password_hash,role,status,created_at,updated_at) VALUES (?,?,?,'synthetic',?,'ACTIVE',now(),now())",entry.getKey(),"r2_"+entry.getKey(),entry.getKey()+"@example.test",entry.getValue());
        var file=directory.resolve("test.key");byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);Files.writeString(file,Base64.getEncoder().encodeToString(key));service=new DocumentIdentityService(jdbc,new TransactionTemplate(new DataSourceTransactionManager(ds)),new DocumentCipher(file.toString()),new ObjectMapper());
    }
    @Test void requiresConfirmedHumanReviewAndExactFieldsOnSameActiveAccount(){var submitted=service.submit(owner,UUID.randomUUID(),fields,true);assertThatThrownBy(()->service.confirmedMatch(owner,fields)).isInstanceOf(BusinessException.class);service.review(staff,submitted.id(),true,true,true,true,"R2-DESK");assertThat(service.confirmedMatch(owner,fields)).isEqualTo(submitted.id());assertThatThrownBy(()->service.confirmedMatch(UUID.randomUUID(),fields)).isInstanceOf(BusinessException.class);assertThatThrownBy(()->service.confirmedMatch(owner,new DocumentIdentityService.Fields("000000000002","TEST PERSON","1990-01-01"))).isInstanceOf(BusinessException.class);jdbc.update("UPDATE identity.users SET status='LOCKED' WHERE id=?",owner);assertThatThrownBy(()->service.confirmedMatch(owner,fields)).isInstanceOf(BusinessException.class);assertThat(jdbc.queryForObject("SELECT sealed_fields FROM identity.document_checks WHERE id=?",byte[].class,submitted.id())).isNull();}
    @Test void rejectedDocumentNeverAuthorizesLinking(){var submitted=service.submit(owner,UUID.randomUUID(),fields,true);service.review(staff,submitted.id(),false,false,false,false,"R2-DESK");assertThatThrownBy(()->service.confirmedMatch(owner,fields)).isInstanceOf(BusinessException.class);}
    @Test void nameCaseAndSpacingDoNotChangeAConfirmedIdentity()throws Exception{
        var input=new DocumentIdentityService.Fields(fields.documentNumber(),"Test   Person",fields.birthDate());
        var submitted=service.submit(owner,UUID.randomUUID(),input,true);service.review(staff,submitted.id(),true,true,true,true,"R2-DESK");
        assertThat(service.confirmedMatch(owner,new DocumentIdentityService.Fields(fields.documentNumber(),"test person",fields.birthDate()))).isEqualTo(submitted.id());
        var legacy=new DocumentIdentityService.Fields(fields.documentNumber(),"Test Person",fields.birthDate());
        var cipher=new DocumentCipher(directory.resolve("test.key").toString());jdbc.update("UPDATE identity.document_checks SET payload_fingerprint=? WHERE id=?",cipher.fingerprint(new ObjectMapper().writeValueAsBytes(legacy)),submitted.id());
        assertThat(service.confirmedMatch(owner,new DocumentIdentityService.Fields(fields.documentNumber(),"test person",fields.birthDate()))).isEqualTo(submitted.id());
        assertThatThrownBy(()->service.confirmedMatch(owner,new DocumentIdentityService.Fields("000000000002","test person",fields.birthDate()))).isInstanceOf(BusinessException.class);
    }

}
