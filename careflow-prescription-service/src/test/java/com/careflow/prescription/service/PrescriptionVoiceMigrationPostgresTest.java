package com.careflow.prescription.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DuplicateKeyException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="R6_DB_TESTS",matches="1")
class PrescriptionVoiceMigrationPostgresTest {
    @Test void upgradingV3PreservesSourcesAndAllowsMoreRecordingsOnTheSamePrescription(){
        var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:15432/careflow_r6_prescription_test",System.getenv("R6_DB_USER"),System.getenv("R6_DB_PASSWORD"));var jdbc=new JdbcTemplate(ds);String schema="voice_repeat_"+UUID.randomUUID().toString().replace("-","");
        try{
            Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).target("3").load().migrate();
            var prescription=UUID.randomUUID();var visit=UUID.randomUUID();var patient=UUID.randomUUID();var doctor=UUID.randomUUID();var first=UUID.randomUUID();
            jdbc.update("INSERT INTO "+schema+".prescriptions(id,created_at,updated_at,consultation_id,patient_id,doctor_id,status) VALUES (?,now(),now(),?,?,?,'DRAFT')",prescription,visit,patient,doctor);
            String insert="INSERT INTO "+schema+".prescription_ai_sources(job_id,transcript_revision,prescription_id,consultation_id,actor_id,result_hash) VALUES (?,1,?,?,?,?)";
            jdbc.update(insert,first,prescription,visit,doctor,"a".repeat(64));
            Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).target("4").load().migrate();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+schema+".prescription_ai_sources",Integer.class)).isEqualTo(1);
            jdbc.update(insert,UUID.randomUUID(),prescription,visit,doctor,"b".repeat(64));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+schema+".prescription_ai_sources WHERE prescription_id=?",Integer.class,prescription)).isEqualTo(2);
            assertThatThrownBy(()->jdbc.update(insert,first,prescription,visit,doctor,"c".repeat(64))).isInstanceOf(DuplicateKeyException.class);
        }finally{jdbc.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}
    }
}
