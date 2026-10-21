package com.careflow.consultation.service;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="R2_DB_TESTS",matches="1")
class ConsultationMigrationPostgresTest {
    DriverManagerDataSource ds;JdbcTemplate jdbc;
    @BeforeEach void setup(){ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:15432/careflow_r2_consultation_test",System.getenv("R2_DB_USER"),System.getenv("R2_DB_PASSWORD"));jdbc=new JdbcTemplate(ds);jdbc.execute("DROP SCHEMA IF EXISTS r2_icd_legacy CASCADE");Flyway.configure().dataSource(ds).schemas("r2_icd_legacy").defaultSchema("r2_icd_legacy").locations("classpath:db/migration").target("4").load().migrate();}
    @AfterEach void cleanup(){jdbc.execute("DROP SCHEMA IF EXISTS r2_icd_legacy CASCADE");}
    void insert(){jdbc.update("INSERT INTO r2_icd_legacy.consultations(id,patient_id,doctor_id,status,created_at,updated_at) VALUES (?,?,?,'COMPLETED',now(),now())",UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());}
    void migrate(){Flyway.configure().dataSource(ds).schemas("r2_icd_legacy").defaultSchema("r2_icd_legacy").locations("classpath:db/migration").load().migrate();}
    @Test void upgradesLegacyOnlyColumnsWithoutLosingDiagnosis(){jdbc.execute("ALTER TABLE r2_icd_legacy.consultations RENAME COLUMN icd10_code TO icd10code");jdbc.execute("ALTER TABLE r2_icd_legacy.consultations RENAME COLUMN icd10_name TO icd10name");insert();jdbc.update("UPDATE r2_icd_legacy.consultations SET icd10code='Z00.0',icd10name='Synthetic diagnosis'");migrate();assertThat(jdbc.queryForObject("SELECT icd10_code FROM r2_icd_legacy.consultations",String.class)).isEqualTo("Z00.0");assertThat(jdbc.queryForObject("SELECT icd10_name FROM r2_icd_legacy.consultations",String.class)).isEqualTo("Synthetic diagnosis");assertThat(jdbc.queryForObject("SELECT icd10code FROM r2_icd_legacy.consultations",String.class)).isEqualTo("Z00.0");}
    @Test void conflictingCanonicalAndLegacyDiagnosesStopUpgradeWithoutOverwriting(){jdbc.execute("ALTER TABLE r2_icd_legacy.consultations ADD COLUMN icd10code varchar(10)");insert();jdbc.update("UPDATE r2_icd_legacy.consultations SET icd10_code='Z00.0',icd10code='Z01.0'");assertThatThrownBy(this::migrate).isInstanceOf(org.flywaydb.core.api.FlywayException.class);assertThat(jdbc.queryForObject("SELECT icd10_code FROM r2_icd_legacy.consultations",String.class)).isEqualTo("Z00.0");assertThat(jdbc.queryForObject("SELECT icd10code FROM r2_icd_legacy.consultations",String.class)).isEqualTo("Z01.0");}
}
