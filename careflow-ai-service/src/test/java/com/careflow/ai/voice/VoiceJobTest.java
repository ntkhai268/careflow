package com.careflow.ai.voice;

import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpServletRequest;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.careflow.ai.voice.VoiceTypes.*;

class VoiceJobTest {
    VoiceJobStore store;JdbcTemplate jdbc;ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    VoiceProperties props;Actor actor;UUID consultation=UUID.randomUUID();
    @BeforeEach void setup() {
        JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("db/voice-migration/V1__voice_prescription_jobs.sql")).execute(ds);
        props=new VoiceProperties();store=new VoiceJobStore(ds,mapper,props);jdbc=new JdbcTemplate(ds);
        actor=new Actor(UUID.randomUUID(),"Bearer not-persisted",UUID.randomUUID().toString());
    }
    Job create(String key){return store.create(consultation,actor,key,"hash","{\"transcript\":\"private text\"}");}
    @Test void durableIdempotencyAndConflict() {
        Job first=create("same");assertThat(create("same").jobId()).isEqualTo(first.jobId());
        assertThatThrownBy(()->store.create(consultation,actor,"same","different","{}"))
            .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(409));
        assertThat(jdbc.queryForObject("select count(*) from voice_prescription_jobs",Integer.class)).isEqualTo(1);
    }
    @Test void resultVersionRevisionAndLateCancellationAreFenced() {
        Job first=create("one");assertThat(store.claim(first)).isTrue();Job running=store.get(first.jobId());
        store.cancel(running);store.finish(running,"SUCCEEDED",mapper.createObjectNode(),null);
        assertThat(store.get(first.jobId()).state()).isEqualTo("CANCELLED");
        assertThat(store.get(first.jobId()).result()).isNull();assertThat(store.get(first.jobId()).inputJson()).isNull();
    }
    @Test void completeThenReviseUsesSameIdAndRejectsOldVersion() {
        Job first=create("one");store.claim(first);Job running=store.get(first.jobId());
        store.finish(running,"SUCCEEDED",mapper.createObjectNode().put("transcript","old"),null);
        Job done=store.get(first.jobId());Job revision=store.revise(done,done.version(),"{\"transcript\":\"new\"}");
        assertThat(revision.jobId()).isEqualTo(first.jobId());assertThat(revision.transcriptRevision()).isEqualTo(2);
        assertThat(revision.result()).isNull();assertThat(revision.version()).isGreaterThan(done.version());
        assertThatThrownBy(()->store.revise(done,done.version(),"{}")).isInstanceOf(BusinessException.class);
    }
    @Test void quotasRestartAndExpiryNeverReplayPersistedCredentials() {
        create("one");create("two");assertThatThrownBy(()->create("three")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(429));
        jdbc.update("update voice_prescription_jobs set lease_until=?",OffsetDateTime.now().minusSeconds(60));
        store.recover();Job job=store.find(actor.userId(),"one");assertThat(job.state()).isEqualTo("FAILED");
        assertThat(job.errorCode()).isEqualTo("SERVER_RESTARTED");assertThat(job.inputJson()).isNull();
        jdbc.update("update voice_prescription_jobs set expires_at=?",OffsetDateTime.now().minusHours(1));store.expire();
        assertThat(store.get(job.jobId()).state()).isEqualTo("EXPIRED");assertThat(store.get(job.jobId()).result()).isNull();
    }
    @Test void ownershipAndHandoverAreRecheckedAfterInference() throws Exception {
        VoiceDependencies deps=mock(VoiceDependencies.class);when(deps.authenticate(any())).thenReturn(actor);
        when(deps.catalog(actor)).thenReturn(request());
        when(deps.infer(any())).thenReturn(result("Cho Paracetamol.",4));
        doNothing().doNothing().doThrow(new BusinessException(403,"assignment changed")).when(deps).authorize(actor,consultation);
        try(VoiceServiceClose close=new VoiceServiceClose(new VoiceJobService(store,deps,mapper,props))) {
            VoiceJobService service=close.service;
            Job job=service.createText(new TextRequest(consultation,"Cho Paracetamol."),"job",new MockHttpServletRequest());
            for(int i=0;i<200&&!Set.of("FAILED","SUCCEEDED").contains(store.get(job.jobId()).state());i++)Thread.sleep(10);
            assertThat(store.get(job.jobId()).state()).isEqualTo("FAILED");
            assertThat(store.get(job.jobId()).errorCode()).isEqualTo("ACCESS_CHANGED");
            when(deps.authenticate(any())).thenReturn(new Actor(UUID.randomUUID(),"Bearer x",actor.correlationId()));
            assertThatThrownBy(()->service.get(job.jobId(),new MockHttpServletRequest())).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(404));
        }
    }
    ObjectNode request() {
        ObjectNode r=mapper.createObjectNode().put("schemaVersion",SCHEMA).put("catalogVersion","v1").put("transcriptRevision",1);
        r.putArray("medicines").addObject().put("code","PARA").put("name","Paracetamol").put("unit","viên").put("active",true);return r;
    }
    ObjectNode result(String text,int start) {
        ObjectNode r=mapper.createObjectNode().put("schemaVersion",SCHEMA).put("transcript",text)
            .put("transcriptHash",VoiceRemoteClient.sha(text)).put("transcriptRevision",1).put("catalogVersion","v1");
        ObjectNode item=r.putArray("items").addObject().put("spokenName","Paracetamol").put("medicineCode","PARA");
        item.putObject("evidence").putObject("spokenName").put("text","Paracetamol").put("start",start).put("end",start+11)
            .put("offsetUnit","unicode_codepoint").put("transcriptHash",VoiceRemoteClient.sha(text)).put("transcriptRevision",1);
        item.putArray("candidates");var proposal=r.putObject("prescriptionDraft").put("canSaveWithoutCorrection",false);proposal.putArray("items");proposal.putArray("blockers");return r;
    }
    @Test void unicodeCodePointEvidenceAndCatalogCannotBeForged() {
        String text="😀 Cho Paracetamol.";ObjectNode valid=result(text,6);
        assertThatCode(()->VoiceResultValidator.validate(valid,request())).doesNotThrowAnyException();
        assertThatThrownBy(()->VoiceResultValidator.validate(result(text,7),request())).isInstanceOf(BusinessException.class);
        ((ObjectNode)valid.path("items").get(0)).put("medicineCode","DAV-FAKE");
        assertThatThrownBy(()->VoiceResultValidator.validate(valid,request())).isInstanceOf(BusinessException.class);
    }
    @Test void payloadAndPrivateTokenNeverReachAudit() {
        Job job=create("one");store.claim(job);Job running=store.get(job.jobId());
        store.finish(running,"SUCCEEDED",mapper.createObjectNode().put("transcript","secret"),null);
        assertThat(jdbc.queryForObject("select count(*) from voice_prescription_audit",Integer.class)).isEqualTo(3);
        assertThat(store.get(job.jobId()).inputJson()).isNull();
        assertThat(jdbc.queryForList("select * from voice_prescription_audit").toString()).doesNotContain("secret","private text","Bearer");
    }
    @Test void anotherInstanceDoesNotRecoverJobsWithAnActiveLease() {
        Job job=create("live");
        // recover is what another instance executes at startup; a live lease is not abandoned.
        store.recover();assertThat(store.get(job.jobId()).state()).isEqualTo("QUEUED");
        store.heartbeat();assertThat(store.claim(job)).isTrue();
    }
    record VoiceServiceClose(VoiceJobService service) implements AutoCloseable {public void close(){service.close();}}
}
