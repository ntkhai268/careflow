package com.careflow.patient.service;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.patient.client.ConfirmedIdentityClient;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@EnabledIfEnvironmentVariable(named="R2_DB_TESTS",matches="1")
class ProfileLinkPostgresTest {
    JdbcTemplate jdbc;TransactionTemplate tx;ProfileLinkService service;
    PatientReceptionService reception;ConfirmedIdentityClient identity;
    final UUID owner=UUID.randomUUID(),staff=UUID.randomUUID(),patient=UUID.randomUUID(),check=UUID.randomUUID();
    final String token=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    @BeforeEach void setup(){
        var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:15432/careflow_r2_patient_test",System.getenv("R2_DB_USER"),System.getenv("R2_DB_PASSWORD"));
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.update("DELETE FROM patient_profile_link_audit");jdbc.update("DELETE FROM patient_profile_links");jdbc.update("DELETE FROM patient_link_authorizations");jdbc.update("DELETE FROM patient_reception_commands");jdbc.update("DELETE FROM patient_reception_audit");jdbc.update("DELETE FROM patients");
        jdbc.update("INSERT INTO patients(id,full_name,date_of_birth,id_card_number) VALUES (?,'Synthetic Patient','1990-01-01','000000000001')",patient);
        reception=mock(PatientReceptionService.class);identity=mock(ConfirmedIdentityClient.class);when(identity.match(any())).thenReturn(ApiResponse.success(new ConfirmedIdentityClient.Result(check)));
        service=new ProfileLinkService(jdbc,reception,identity);
    }
    UUID proposal(){
        tx.execute(t->service.authorize(owner,"PATIENT",UUID.randomUUID(),token));UUID id=UUID.randomUUID();
        tx.execute(t->service.propose(id,patient,"ROOM-01",token,"TEST-DESK",true,staff,"STAFF"));return id;
    }
    UUID storedOwner(){return jdbc.queryForObject("SELECT user_id FROM patients WHERE id=?",UUID.class,patient);}
    void denied(int status,Runnable action){assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(status));}
    @Test void codeAloneNeverGrantsMedicalAccessAndOnlyHashIsStored(){UUID id=proposal();assertThat(storedOwner()).isNull();assertThat(service.mine(owner,"PATIENT").getFirst().status()).isEqualTo("PENDING_OWNER");assertThat(jdbc.queryForObject("SELECT token_hash FROM patient_link_authorizations",String.class)).hasSize(64).isNotEqualTo(token);verify(identity).match(any());}
    @Test void ownerConsentGrantsThenRevocationClearsOwnershipWithoutDeletingHistory(){UUID id=proposal();assertThat(tx.execute(t->service.decide(id,true,owner,"PATIENT")).status()).isEqualTo("LINKED");assertThat(storedOwner()).isEqualTo(owner);assertThat(tx.execute(t->service.revoke(id,owner,"PATIENT")).status()).isEqualTo("REVOKED");assertThat(storedOwner()).isNull();assertThat(jdbc.queryForObject("SELECT count(*) FROM patients WHERE id=?",Integer.class,patient)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM patient_profile_link_audit WHERE link_id=?",Integer.class,id)).isEqualTo(3);}
    @Test void otherAccountCannotApproveOrRevoke(){UUID id=proposal();denied(403,()->tx.execute(t->service.decide(id,true,UUID.randomUUID(),"PATIENT")));tx.execute(t->service.decide(id,true,owner,"PATIENT"));denied(403,()->tx.execute(t->service.revoke(id,UUID.randomUUID(),"PATIENT")));assertThat(storedOwner()).isEqualTo(owner);}
    @Test void expiryAndOneTimeCodeAreEnforced(){UUID id=proposal();UUID other=UUID.randomUUID();jdbc.update("INSERT INTO patients(id,full_name,date_of_birth,id_card_number) VALUES (?,'Synthetic Patient','1990-01-01','000000000002')",other);denied(409,()->tx.execute(t->service.propose(UUID.randomUUID(),other,"ROOM-01",token,"TEST-DESK",true,staff,"STAFF")));jdbc.update("UPDATE patient_profile_links SET expires_at=now()-interval '1 minute' WHERE id=?",id);assertThat(tx.execute(t->service.decide(id,true,owner,"PATIENT")).status()).isEqualTo("EXPIRED");assertThat(storedOwner()).isNull();}
    @Test void duplicateRequiresReviewWithoutGrantAndCanBeRejected(){UUID id=proposal();jdbc.update("INSERT INTO patients(id,user_id,full_name,id_card_number) VALUES (?,?,'Synthetic Duplicate','000000000001')",UUID.randomUUID(),owner);assertThat(tx.execute(t->service.decide(id,true,owner,"PATIENT")).status()).isEqualTo("DUPLICATE_REVIEW");assertThat(storedOwner()).isNull();assertThat(tx.execute(t->service.rejectReview(id,staff,"STAFF")).status()).isEqualTo("REJECTED");}
    @Test void revocationStillWorksAfterAnotherUnownedProfileHasSameCard(){UUID id=proposal();tx.execute(t->service.decide(id,true,owner,"PATIENT"));jdbc.update("INSERT INTO patients(id,full_name,id_card_number) VALUES (?,'Synthetic Duplicate','000000000001')",UUID.randomUUID());tx.execute(t->service.revoke(id,owner,"PATIENT"));assertThat(storedOwner()).isNull();}
    @Test void identityMismatchCannotConsumeCodeOrCreateRequest(){when(identity.match(any())).thenThrow(new BusinessException(409,"Mismatch"));tx.execute(t->service.authorize(owner,"PATIENT",UUID.randomUUID(),token));denied(409,()->tx.execute(t->service.propose(UUID.randomUUID(),patient,"ROOM-01",token,"TEST-DESK",true,staff,"STAFF")));assertThat(jdbc.queryForObject("SELECT count(*) FROM patient_profile_links",Integer.class)).isZero();assertThat(jdbc.queryForObject("SELECT used_at FROM patient_link_authorizations",java.sql.Timestamp.class)).isNull();}
    @Test void removedStaffAssignmentCannotProposeOrRevoke(){doThrow(new BusinessException(403,"Unassigned")).when(reception).requireRoom("ROOM-01",staff,"STAFF");denied(403,()->tx.execute(t->service.propose(UUID.randomUUID(),patient,"ROOM-01",token,"TEST-DESK",true,staff,"STAFF")));verifyNoInteractions(identity);}
    @Test void concurrentOwnerApprovalAndRepeatedCommandsWriteOneGrant()throws Exception{
        UUID id=proposal();var pool=Executors.newFixedThreadPool(2);
        try{var outcomes=pool.invokeAll(List.<Callable<String>>of(()->tx.execute(t->service.decide(id,true,owner,"PATIENT")).status(),()->tx.execute(t->service.decide(id,true,owner,"PATIENT")).status()));assertThat(outcomes.get(0).get()).isEqualTo("LINKED");assertThat(outcomes.get(1).get()).isEqualTo("LINKED");}finally{pool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patient_profile_link_audit WHERE action='LINKED'",Integer.class)).isEqualTo(1);
    }
    @Test void proposalReplayDoesNotUseAuthorizationAgain(){UUID id=proposal();assertThat(tx.execute(t->service.propose(id,patient,"ROOM-01",token,"TEST-DESK",true,staff,"STAFF")).id()).isEqualTo(id);verify(identity,times(1)).match(any());denied(409,()->tx.execute(t->service.propose(id,patient,"ROOM-01",token,"CHANGED",true,staff,"STAFF")));}
}
