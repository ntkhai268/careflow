package com.careflow.ai.clinical;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/** Demo data stays DRAFT; startup never asserts professional approval. */
@Component
@Profile({"demo","test"})
@ConditionalOnProperty(name="careflow.clinical.enabled",havingValue="true")
public class ClinicalDemoDataInitializer implements CommandLineRunner {
    private final ClinicalStore store;
    private final JdbcTemplate jdbc;
    public ClinicalDemoDataInitializer(ClinicalStore store,javax.sql.DataSource source){this.store=store;jdbc=new JdbcTemplate(source);}
    @Override public void run(String... args){
        String title="SYNTHETIC CAREFLOW SOFTWARE TEST";
        if(jdbc.queryForObject("SELECT count(*) FROM ai_guideline_sources WHERE title=?",Integer.class,title)==0){
            store.create(title,LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString(),"NOI_TONG_QUAT","https://example.org/careflow-test","Synthetic software fixture only. Not a clinical guideline.",true,UUID.fromString("00000000-0000-0000-0000-000000000006"));
        }
    }
}
