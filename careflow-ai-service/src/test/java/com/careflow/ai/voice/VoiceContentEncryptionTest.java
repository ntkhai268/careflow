package com.careflow.ai.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class VoiceContentEncryptionTest {
    @Test void persistedContentIsEncryptedAndSurvivesRestartWithTheSameKey() {
        var ds=new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("db/voice-migration/V1__voice_prescription_jobs.sql")).execute(ds);
        var props=new VoiceProperties();props.setContentKey(Base64.getEncoder().encodeToString(new byte[32]));
        var json=new ObjectMapper().findAndRegisterModules();
        var store=new VoiceJobStore(ds,json,props);var jdbc=new JdbcTemplate(ds);
        var actor=new VoiceTypes.Actor(UUID.randomUUID(),"Bearer never-persisted",UUID.randomUUID().toString());
        var job=store.create(UUID.randomUUID(),actor,"fixture","hash","SYNTHETIC_PRIVATE_AUDIO");
        assertThat(jdbc.queryForObject("SELECT input_json FROM voice_prescription_jobs",String.class)).startsWith("ENC1:").doesNotContain("SYNTHETIC_PRIVATE_AUDIO");
        assertThat(new VoiceJobStore(ds,json,props).get(job.jobId()).inputJson()).isEqualTo("SYNTHETIC_PRIVATE_AUDIO");
        store.claim(job);store.finish(store.get(job.jobId()),"SUCCEEDED",json.createObjectNode().put("transcript","SYNTHETIC_RESULT"),null);
        assertThat(jdbc.queryForObject("SELECT result_json FROM voice_prescription_jobs",String.class)).doesNotContain("SYNTHETIC_RESULT");
        assertThat(new VoiceJobStore(ds,json,props).get(job.jobId()).result().path("transcript").asText()).isEqualTo("SYNTHETIC_RESULT");
        var wrong=new VoiceProperties();wrong.setContentKey(Base64.getEncoder().encodeToString("11111111111111111111111111111111".getBytes()));
        assertThatThrownBy(()->new VoiceJobStore(ds,json,wrong).get(job.jobId())).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForList("SELECT * FROM voice_prescription_audit").toString()).doesNotContain("SYNTHETIC_RESULT","PRIVATE_AUDIO","Bearer");
    }
}
