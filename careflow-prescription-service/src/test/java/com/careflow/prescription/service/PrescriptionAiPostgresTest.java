package com.careflow.prescription.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.prescription.config.*;
import com.careflow.prescription.controller.PrescriptionReleaseController;
import com.careflow.prescription.mapper.PrescriptionMapper;
import com.careflow.prescription.model.*;
import com.careflow.prescription.dto.request.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.careflow.prescription.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringJUnitConfig(PrescriptionAiPostgresTest.Config.class)
@EnabledIfEnvironmentVariable(named="R6_DB_TESTS",matches="1")
class PrescriptionAiPostgresTest {
    @Configuration @EnableTransactionManagement @EnableJpaRepositories(basePackages="com.careflow.prescription.repository")
    static class Config {
        @Bean DataSource dataSource(){var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:15432/careflow_r6_prescription_test",System.getenv("R6_DB_USER"),System.getenv("R6_DB_PASSWORD"));Flyway.configure().dataSource(ds).schemas("public").baselineOnMigrate(true).baselineVersion("0").load().migrate();return ds;}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){var em=new LocalContainerEntityManagerFactoryBean();em.setDataSource(ds);em.setPackagesToScan("com.careflow.prescription.model");em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());em.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return em;}
        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory em){return new JpaTransactionManager(em);}
        @Bean JdbcTemplate jdbcTemplate(DataSource ds){return new JdbcTemplate(ds);}
        @Bean ObjectMapper objectMapper(){return new ObjectMapper().findAndRegisterModules();}
        @Bean RabbitTemplate rabbitTemplate(){return mock(RabbitTemplate.class);}
        @Bean ConsultationClient consultations(){return mock(ConsultationClient.class);}
        @Bean AppointmentClient appointments(){return mock(AppointmentClient.class);}
        @Bean DirectoryClient directory(){return mock(DirectoryClient.class);}
        @Bean PrescriptionAiReleaseGuard aiGuard(JdbcTemplate jdbc){return new PrescriptionAiReleaseGuard(jdbc);}
        @Bean PrescriptionAiClient ai(){return mock(PrescriptionAiClient.class);}
        @Bean PrescriptionAiDraftService aiDrafts(JdbcTemplate jdbc,PrescriptionAiClient ai,ConsultationClient c,PrescriptionService p,DrugRepository drugs){return new PrescriptionAiDraftService(jdbc,ai,c,p,drugs);}
        @Bean PrescriptionMapper mapper(){return new PrescriptionMapper();}
        @Bean PrescriptionEventOutbox outbox(JdbcTemplate jdbc,ObjectMapper json,RabbitTemplate rabbit,PlatformTransactionManager manager){return new PrescriptionEventOutbox(jdbc,json,rabbit,manager);}
        @Bean PrescriptionService prescriptions(PrescriptionRepository repo,DrugRepository drugs,PrescriptionMapper mapper,ConsultationClient consultations,DirectoryClient directory,ObjectMapper json,PrescriptionEventOutbox outbox,PrescriptionAiReleaseGuard guard){return new PrescriptionService(repo,drugs,mapper,consultations,directory,mock(QueueExecutionClient.class),mock(PatientIdentityClient.class),json,outbox,guard);}
        @Bean PrescriptionFollowUpService followUps(PrescriptionRepository repo,ConsultationClient consultations,AppointmentClient appointments,PrescriptionMapper mapper){return new PrescriptionFollowUpService(repo,consultations,appointments,mapper);}
    }

 @Autowired PrescriptionAiDraftService drafts;@Autowired PrescriptionAiClient ai;@Autowired ConsultationClient consultations;@Autowired DirectoryClient directory;@Autowired PrescriptionService prescriptions;@Autowired DrugRepository drugs;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;
 final UUID actor=UUID.randomUUID(),visit=UUID.randomUUID(),patient=UUID.randomUUID(),job=UUID.randomUUID();
 @BeforeEach void fixture(){jdbc.execute("TRUNCATE prescription_ai_sources,prescription_items,prescriptions,prescription_event_outbox CASCADE");reset(ai,consultations,directory);when(consultations.getConsultation(visit)).thenReturn(new ConsultationClient.ConsultationContext(visit,patient,actor,"IN_PROGRESS"));when(directory.getActivePharmacyServicePointId()).thenReturn("PHARMACY-MAIN-01");if(drugs.findByCodeAndActiveTrue("R6-DRUG").isEmpty())drugs.saveAndFlush(Drug.builder().code("R6-DRUG").name("R6 SYNTHETIC DRUG").unit("viên").active(true).build());when(ai.job(job)).thenReturn(result(true));}
 ObjectNode result(boolean complete){var n=json.createObjectNode().put("jobId",job.toString()).put("consultationId",visit.toString()).put("transcriptRevision",1).put("state","SUCCEEDED").put("expiresAt",java.time.Instant.now().plusSeconds(600).toString());var r=n.putObject("result").put("schemaVersion","careflow.voice-prescription.v1");r.putArray("items").addObject().put("medicineCode","R6-DRUG").putObject("fieldConflicts");var item=r.putObject("prescriptionDraft").putArray("items").addObject().put("sourceItemIndex",0).put("medicineCode","R6-DRUG").put("medicineName","R6 SYNTHETIC DRUG").put("unit","viên");item.putObject("unmappedFields").put("route","SYNTHETIC ROUTE");if(complete)item.put("dosage","fixture dose").put("frequency","fixture frequency").put("timing","fixture timing").put("duration",2).put("quantity",4);return n;}
 @Test void duplicateConcurrentSaveCreatesOneDraftAndOneSourceLink()throws Exception{var pool=Executors.newFixedThreadPool(2);try{var list=pool.invokeAll(List.<Callable<UUID>>of(()->drafts.save(job,1,actor,"DOCTOR").getId(),()->drafts.save(job,1,actor,"DOCTOR").getId()));assertThat(list.get(0).get()).isEqualTo(list.get(1).get());assertThat(jdbc.queryForObject("SELECT count(*) FROM prescriptions",Integer.class)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_ai_sources",Integer.class)).isEqualTo(1);}finally{pool.shutdownNow();}}
 @Test void retryPreservesHumanEditsAndNamedVoiceDetails(){var first=drafts.save(job,1,actor,"DOCTOR");var i=first.getItems().getFirst();assertThat(i.getDetails()).containsEntry("route","SYNTHETIC ROUTE");var changed=PrescriptionItemRequest.builder().medicineCode(i.getMedicineCode()).medicineName(i.getMedicineName()).unit(i.getUnit()).dosage("HUMAN CORRECTION").frequency(i.getFrequency()).timing(i.getTiming()).duration(3).quantity(9).details(Map.of("route","HUMAN ROUTE")).build();prescriptions.updatePrescription(first.getId(),CreatePrescriptionRequest.builder().consultationId(visit).patientId(patient).doctorId(actor).items(List.of(changed)).build(),actor,"DOCTOR");var retry=drafts.save(job,1,actor,"DOCTOR");assertThat(retry.getId()).isEqualTo(first.getId());assertThat(retry.getItems().getFirst().getDosage()).isEqualTo("HUMAN CORRECTION");assertThat(retry.getItems().getFirst().getDetails()).containsEntry("route","HUMAN ROUTE");verify(ai,times(1)).job(job);}
 @Test void incompleteUnambiguousDraftCanBeSavedButCannotBeSigned(){when(ai.job(job)).thenReturn(result(false));var p=drafts.save(job,1,actor,"DOCTOR");assertThat(p.getItems().getFirst().getQuantity()).isNull();assertThatThrownBy(()->prescriptions.confirmPrescription(p.getId(),actor,"DOCTOR")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(422));assertThat(prescriptions.getPrescription(p.getId(),actor,"DOCTOR").getStatus()).isEqualTo(PrescriptionStatus.DRAFT);assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_event_outbox",Integer.class)).isZero();}
 @Test void sourceAmbiguityAndVersionMismatchDoNotCreatePrescription(){var r=result(true);((ObjectNode)r.path("result").path("items").get(0).path("fieldConflicts")).put("dose","conflicting");when(ai.job(job)).thenReturn(r);assertThatThrownBy(()->drafts.save(job,1,actor,"DOCTOR")).isInstanceOf(BusinessException.class);assertThatThrownBy(()->drafts.save(job,2,actor,"DOCTOR")).isInstanceOf(BusinessException.class);assertThat(jdbc.queryForObject("SELECT count(*) FROM prescriptions",Integer.class)).isZero();}
 @Test void dependencyFailureAndWrongRoleNeverCreateDraft(){when(ai.job(job)).thenThrow(new BusinessException(503,"fixture outage"));assertThatThrownBy(()->drafts.save(job,1,actor,"DOCTOR")).isInstanceOf(BusinessException.class);assertThatThrownBy(()->drafts.save(job,1,actor,"PATIENT")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(403));assertThat(jdbc.queryForObject("SELECT count(*) FROM prescriptions",Integer.class)).isZero();}
 @Test void formerDoctorCannotRetryAfterHandover(){drafts.save(job,1,actor,"DOCTOR");when(consultations.getConsultation(visit)).thenReturn(new ConsultationClient.ConsultationContext(visit,patient,UUID.randomUUID(),"IN_PROGRESS"));assertThatThrownBy(()->drafts.save(job,1,actor,"DOCTOR")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(403));}

 @Test void editableDraftKeepsUnmatchedMedicineInsteadOfDroppingIt(){
     var r=result(true);var sources=(com.fasterxml.jackson.databind.node.ArrayNode)r.path("result").path("items");
     ((ObjectNode)sources.get(0)).put("spokenName","R6 SYNTHETIC DRUG").put("dose","fixture dose").put("frequency","fixture frequency").put("timing","fixture timing").put("quantity",4);
     sources.addObject().put("spokenName","SYNTHETIC NAMED DRUG").put("strength","fixture strength").put("dosageForm","fixture form").put("quantity",3.0).put("duration","dùng trong 3 ngày").put("quantityUnit","viên").putObject("fieldConflicts");when(ai.job(job)).thenReturn(r);
     var p=drafts.saveEditable(job,1,actor,"DOCTOR");assertThat(p.getStatus()).isEqualTo(PrescriptionStatus.DRAFT);assertThat(p.getItems()).hasSize(2);
     var named=p.getItems().get(1);assertThat(named.getMedicineName()).isEqualTo("SYNTHETIC NAMED DRUG");assertThat(named.getMedicineCode()).isNull();assertThat(named.getDetails()).containsEntry("strength","fixture strength").containsEntry("dosageForm","fixture form");assertThat(named.getQuantity()).isEqualTo(3);assertThat(named.getDuration()).isEqualTo(3);
     assertThat(drafts.saveEditable(job,1,actor,"DOCTOR").getId()).isEqualTo(p.getId());assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_ai_sources",Integer.class)).isEqualTo(1);assertThat(drugs.findByCodeAndActiveTrue("SYNTHETIC NAMED DRUG")).isEmpty();
 }

 @Test void editableConflictIsBlankAndCanBeCorrectedOnTheSameDraft(){
     var r=result(true);var source=(ObjectNode)r.path("result").path("items").get(0);source.put("spokenName","R6 SYNTHETIC DRUG").put("frequency","fixture frequency").put("timing","fixture timing").put("quantity",4);source.withObject("fieldConflicts").putArray("dose").addObject().put("text","conflicting dose");when(ai.job(job)).thenReturn(r);
     var p=drafts.saveEditable(job,1,actor,"DOCTOR");assertThat(p.getItems().getFirst().getDosage()).isNull();assertThatThrownBy(()->prescriptions.confirmPrescription(p.getId(),actor,"DOCTOR")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(422));
     var fixed=PrescriptionItemRequest.builder().medicineCode("R6-DRUG").medicineName("R6 SYNTHETIC DRUG").unit("viên").dosage("HUMAN CORRECTION").frequency("fixture frequency").timing("fixture timing").duration(2).quantity(4).build();
     prescriptions.updatePrescription(p.getId(),CreatePrescriptionRequest.builder().consultationId(visit).patientId(patient).doctorId(actor).items(List.of(fixed)).build(),actor,"DOCTOR");assertThat(drafts.saveEditable(job,1,actor,"DOCTOR").getItems().getFirst().getDosage()).isEqualTo("HUMAN CORRECTION");assertThat(prescriptions.confirmPrescription(p.getId(),actor,"DOCTOR").getStatus()).isEqualTo(PrescriptionStatus.CONFIRMED);
 }

 @Test void namedMedicineNeedsExplicitReviewBeforeReleaseAndPreservesEditsOnRetry(){
     var r=result(true);var source=(ObjectNode)r.path("result").path("items").get(0);source.remove("medicineCode");source.put("spokenName","SYNTHETIC NAMED DRUG").put("quantityUnit","viên");when(ai.job(job)).thenReturn(r);
     var p=drafts.saveEditable(job,1,actor,"DOCTOR");var fixed=PrescriptionItemRequest.builder().medicineName("HUMAN CORRECTED NAME").unit("viên").dosage("fixture dose").frequency("fixture frequency").timing("fixture timing").duration(2).quantity(7).build();
     prescriptions.updatePrescription(p.getId(),CreatePrescriptionRequest.builder().consultationId(visit).patientId(patient).doctorId(actor).items(List.of(fixed)).build(),actor,"DOCTOR");assertThatThrownBy(()->prescriptions.confirmPrescription(p.getId(),actor,"DOCTOR")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(422));assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_event_outbox",Integer.class)).isZero();
     fixed.setDetails(Map.of("manualReview","confirmed"));prescriptions.updatePrescription(p.getId(),CreatePrescriptionRequest.builder().consultationId(visit).patientId(patient).doctorId(actor).items(List.of(fixed)).build(),actor,"DOCTOR");assertThat(drafts.saveEditable(job,1,actor,"DOCTOR").getItems().getFirst().getMedicineName()).isEqualTo("HUMAN CORRECTED NAME");assertThat(prescriptions.confirmPrescription(p.getId(),actor,"DOCTOR").getStatus()).isEqualTo(PrescriptionStatus.CONFIRMED);assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_event_outbox",Integer.class)).isEqualTo(1);
 }

 @Test void editableDraftStillRejectsWrongRoleExpiredSourceAndChangedAssignment(){
     var r=result(true);((ObjectNode)r.path("result").path("items").get(0)).put("spokenName","R6 SYNTHETIC DRUG");when(ai.job(job)).thenReturn(r);
     assertThatThrownBy(()->drafts.saveEditable(job,1,actor,"PATIENT")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(403));
     r.put("expiresAt",java.time.Instant.now().minusSeconds(1).toString());assertThatThrownBy(()->drafts.saveEditable(job,1,actor,"DOCTOR")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(409));
     r.put("expiresAt",java.time.Instant.now().plusSeconds(600).toString());var p=drafts.saveEditable(job,1,actor,"DOCTOR");when(consultations.getConsultation(visit)).thenReturn(new ConsultationClient.ConsultationContext(visit,patient,UUID.randomUUID(),"IN_PROGRESS"));assertThatThrownBy(()->drafts.saveEditable(job,1,actor,"DOCTOR")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(403));assertThat(p.getId()).isNotNull();
 }

 ObjectNode additionalResult(UUID id){var r=result(true).put("jobId",id.toString());((ObjectNode)r.path("result").path("items").get(0)).put("spokenName","R6 SYNTHETIC DRUG").put("dose","fixture additional dose").put("frequency","fixture frequency").put("timing","fixture timing").put("quantity",4.0).put("duration","dùng trong 2 ngày");return r;}

 @Test void additionalRecordingAppendsOnceAndPreservesHumanEdits(){
     var p=drafts.save(job,1,actor,"DOCTOR");var item=PrescriptionItemRequest.builder().medicineCode("R6-DRUG").medicineName("R6 SYNTHETIC DRUG").unit("viên").dosage("HUMAN DOSE").frequency("HUMAN FREQUENCY").timing("HUMAN TIMING").quantity(9).duration(3).details(Map.of("route","HUMAN ROUTE","manualReview","confirmed")).build();
     prescriptions.updatePrescription(p.getId(),CreatePrescriptionRequest.builder().consultationId(visit).patientId(patient).doctorId(actor).diagnosis("HUMAN DIAGNOSIS").notes("HUMAN NOTES").items(List.of(item)).build(),actor,"DOCTOR");
     var next=UUID.randomUUID();when(ai.job(next)).thenReturn(additionalResult(next));var appended=drafts.appendEditable(next,1,actor,"DOCTOR",p.getId());
     assertThat(appended.getId()).isEqualTo(p.getId());assertThat(appended.getItems()).hasSize(2);assertThat(appended.getItems().getFirst().getDosage()).isEqualTo("HUMAN DOSE");assertThat(appended.getItems().getFirst().getQuantity()).isEqualTo(9);assertThat(appended.getItems().getFirst().getDetails()).containsEntry("manualReview","confirmed");assertThat(appended.getDiagnosis()).isEqualTo("HUMAN DIAGNOSIS");assertThat(appended.getNotes()).isEqualTo("HUMAN NOTES");
     assertThat(drafts.appendEditable(next,1,actor,"DOCTOR",p.getId()).getItems()).hasSize(2);assertThat(jdbc.queryForObject("SELECT count(*) FROM prescriptions",Integer.class)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_ai_sources",Integer.class)).isEqualTo(2);verify(ai,times(1)).job(next);
 }

 @Test void concurrentDifferentRecordingsDoNotLoseRowsOrDuplicateRetries()throws Exception{
     var p=drafts.save(job,1,actor,"DOCTOR");var a=UUID.randomUUID();var b=UUID.randomUUID();when(ai.job(a)).thenReturn(additionalResult(a));when(ai.job(b)).thenReturn(additionalResult(b));var pool=Executors.newFixedThreadPool(3);
     try{var results=pool.invokeAll(List.<Callable<UUID>>of(()->drafts.appendEditable(a,1,actor,"DOCTOR",p.getId()).getId(),()->drafts.appendEditable(b,1,actor,"DOCTOR",p.getId()).getId(),()->drafts.appendEditable(a,1,actor,"DOCTOR",p.getId()).getId()));for(var result:results)assertThat(result.get()).isEqualTo(p.getId());}finally{pool.shutdownNow();}
     assertThat(prescriptions.getPrescription(p.getId(),actor,"DOCTOR").getItems()).hasSize(3);assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_ai_sources",Integer.class)).isEqualTo(3);
 }

 @Test void additionalRecordingRejectsAnotherVisitWrongRoleAndAnotherLinkedTarget(){
     var p=drafts.save(job,1,actor,"DOCTOR");var otherVisit=UUID.randomUUID();when(consultations.getConsultation(otherVisit)).thenReturn(new ConsultationClient.ConsultationContext(otherVisit,patient,actor,"IN_PROGRESS"));var original=p.getItems().getFirst();var row=PrescriptionItemRequest.builder().medicineCode(original.getMedicineCode()).medicineName(original.getMedicineName()).unit(original.getUnit()).build();var other=prescriptions.createPrescription(CreatePrescriptionRequest.builder().consultationId(otherVisit).patientId(patient).doctorId(actor).items(List.of(row)).build(),actor,"DOCTOR");var next=UUID.randomUUID();when(ai.job(next)).thenReturn(additionalResult(next));
     assertThatThrownBy(()->drafts.appendEditable(next,1,actor,"PATIENT",p.getId())).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(403));
     assertThatThrownBy(()->drafts.appendEditable(next,1,actor,"DOCTOR",other.getId())).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(403));
     assertThatThrownBy(()->drafts.appendEditable(job,1,actor,"DOCTOR",other.getId())).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(409));assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_ai_sources",Integer.class)).isEqualTo(1);
 }

 @Test void signedPrescriptionRejectsNewRecordingButAnAlreadySavedRetryRemainsIdempotent(){
     var p=drafts.save(job,1,actor,"DOCTOR");prescriptions.confirmPrescription(p.getId(),actor,"DOCTOR");var next=UUID.randomUUID();when(ai.job(next)).thenReturn(additionalResult(next));assertThatThrownBy(()->drafts.appendEditable(next,1,actor,"DOCTOR",p.getId())).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(409));assertThat(drafts.appendEditable(job,1,actor,"DOCTOR",p.getId()).getStatus()).isEqualTo(PrescriptionStatus.CONFIRMED);assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_ai_sources",Integer.class)).isEqualTo(1);
 }

 @Test void repeatedRecordingCannotExceedFiftyItems(){
     var rows=new ArrayList<PrescriptionItemRequest>();for(int i=0;i<50;i++)rows.add(PrescriptionItemRequest.builder().medicineCode("R6-DRUG").medicineName("R6 SYNTHETIC DRUG").unit("viên").build());var p=prescriptions.createPrescription(CreatePrescriptionRequest.builder().consultationId(visit).patientId(patient).doctorId(actor).items(rows).build(),actor,"DOCTOR");var next=UUID.randomUUID();when(ai.job(next)).thenReturn(additionalResult(next));assertThatThrownBy(()->drafts.appendEditable(next,1,actor,"DOCTOR",p.getId())).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(422));assertThat(prescriptions.getPrescription(p.getId(),actor,"DOCTOR").getItems()).hasSize(50);assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_ai_sources",Integer.class)).isZero();
 }
 @Test void clearedDraftCanBeReplacedAtomicallyByNewSourceWithoutResurrectingDeletedRows(){
     var first=drafts.save(job,1,actor,"DOCTOR");var next=UUID.randomUUID();var source=additionalResult(next);((ObjectNode)source.path("result").path("items").get(0)).put("spokenName","NEW NAMED MEDICINE").remove("medicineCode");when(ai.job(next)).thenReturn(source);
     var replaced=drafts.replaceEditable(next,1,actor,"DOCTOR",first.getId());assertThat(replaced.getId()).isEqualTo(first.getId());assertThat(replaced.getItems()).hasSize(1);assertThat(replaced.getItems().getFirst().getMedicineName()).isEqualTo("NEW NAMED MEDICINE");assertThat(drafts.replaceEditable(next,1,actor,"DOCTOR",first.getId()).getItems()).hasSize(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_ai_sources",Integer.class)).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT count(*) FROM prescriptions",Integer.class)).isEqualTo(1);
 }
 @Test void invalidReplacementKeepsExistingDraftAndCannotReplaceSignedPrescription(){
     var first=drafts.save(job,1,actor,"DOCTOR");var next=UUID.randomUUID();var bad=additionalResult(next);((com.fasterxml.jackson.databind.node.ArrayNode)bad.path("result").path("items")).removeAll();when(ai.job(next)).thenReturn(bad);assertThatThrownBy(()->drafts.replaceEditable(next,1,actor,"DOCTOR",first.getId())).isInstanceOf(BusinessException.class);assertThat(prescriptions.getPrescription(first.getId(),actor,"DOCTOR").getItems()).hasSize(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_ai_sources",Integer.class)).isEqualTo(1);
     prescriptions.confirmPrescription(first.getId(),actor,"DOCTOR");when(ai.job(next)).thenReturn(additionalResult(next));assertThatThrownBy(()->drafts.replaceEditable(next,1,actor,"DOCTOR",first.getId())).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getStatus()).isEqualTo(409));assertThatThrownBy(()->drafts.replaceEditable(next,1,actor,"PATIENT",first.getId())).isInstanceOf(BusinessException.class);
 }

}
