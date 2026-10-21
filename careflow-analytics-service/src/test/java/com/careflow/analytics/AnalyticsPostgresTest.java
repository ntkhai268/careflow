package com.careflow.analytics;
import com.careflow.common.analytics.*;
import com.careflow.common.analytics.AnalyticsSnapshot.Source;
import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
@EnabledIfEnvironmentVariable(named="R5_DB_TESTS",matches="1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnalyticsPostgresTest {
    static final LocalDate DAY=LocalDate.of(2026,10,21);
    AnnotationConfigApplicationContext context;SnapshotStore store;JdbcTemplate jdbc;EventIngestion ingestion;SourceClient sources;ReconciliationService reconciliation;AnalyticsReportService reports;ObjectMapper json;
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource(){var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:15432/careflow_r5_analytics_test",System.getenv("R5_DB_USER"),System.getenv("R5_DB_PASSWORD"));Flyway.configure().dataSource(ds).schemas("analytics").defaultSchema("analytics").locations("classpath:db/migration").load().migrate();return ds;}
        @Bean PlatformTransactionManager transactionManager(DataSource ds){return new DataSourceTransactionManager(ds);}
        @Bean JdbcTemplate jdbc(DataSource ds){return new JdbcTemplate(ds);}
        @Bean ObjectMapper json(){return new ObjectMapper().findAndRegisterModules();}
        @Bean SnapshotStore store(JdbcTemplate jdbc,ObjectMapper json){return new SnapshotStore(jdbc,json);}
        @Bean EventIngestion ingestion(SnapshotStore s){return new EventIngestion(s);}
        @Bean SourceClient sources(){return mock(SourceClient.class);}
        @Bean AnalyticsReportService reports(SnapshotStore s){return new AnalyticsReportService(s,false);}
        @Bean ReconciliationService reconciliation(DataSource ds,JdbcTemplate jdbc,SourceClient client,SnapshotStore store){return new ReconciliationService(ds,jdbc,client,store,false,false);}
    }
    @BeforeAll void open(){context=new AnnotationConfigApplicationContext(Config.class);store=context.getBean(SnapshotStore.class);jdbc=context.getBean(JdbcTemplate.class);ingestion=context.getBean(EventIngestion.class);sources=context.getBean(SourceClient.class);reconciliation=context.getBean(ReconciliationService.class);reports=context.getBean(AnalyticsReportService.class);json=context.getBean(ObjectMapper.class);}
    @AfterAll void close(){if(context!=null)context.close();}
    @BeforeEach void clear(){jdbc.execute("TRUNCATE analytics.source_projection,analytics.snapshot_history,analytics.event_receipts,analytics.operation_audit RESTART IDENTITY CASCADE");jdbc.update("UPDATE analytics.sync_state SET cursor_id=NULL,cycle_rows=0,started_at=NULL,last_success_at=NULL,status='NOT_STARTED',last_error_code=NULL");reset(sources);}
    AnalyticsSnapshot changed(AnalyticsSnapshot v,String key,Object value){ObjectNode node=json.valueToTree(v);node.set(key,json.valueToTree(value));try{return json.treeToValue(node,AnalyticsSnapshot.class);}catch(Exception e){throw new RuntimeException(e);}}
    void fixture(boolean demo){DemoSnapshots.forDay(DAY).forEach(v->store.accept(v,demo));}
    @Test void actualDatabaseProducesKnownCohortAndDoesNotMixSynthetic(){fixture(true);assertThat(reports.report(DAY,DAY,null,null).summary().booked()).isZero();fixture(false);var m=reports.report(DAY,DAY,null,null).summary();assertThat(m.booked()).isEqualTo(2);assertThat(m.completed()).isEqualTo(2);assertThat(m.initialWait().meanMinutes()).isEqualTo(7.5);assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics.source_projection",Integer.class)).isEqualTo(14);}
    @Test void retryAndConcurrentDeliveryHaveOneReceiptAndProjection()throws Exception{var v=DemoSnapshots.forDay(DAY).getFirst();UUID event=UUID.randomUUID();var pool=Executors.newFixedThreadPool(2);try{var futures=pool.invokeAll(List.<Callable<Void>>of(()->{ingestion.commit(v.source(),event,v.id(),Optional.of(v));return null;},()->{ingestion.commit(v.source(),event,v.id(),Optional.of(v));return null;}));for(var f:futures)f.get();}finally{pool.shutdownNow();}assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics.event_receipts",Integer.class)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics.source_projection",Integer.class)).isEqualTo(1);}
    @Test void oldSnapshotCannotUndoCancelledBooking(){var a=DemoSnapshots.forDay(DAY).getFirst();var cancelled=changed(changed(a,"status","CANCELLED"),"updatedAt",a.updatedAt().plusSeconds(10));store.accept(cancelled,false);assertThat(store.accept(a,false)).isEqualTo("STALE_SNAPSHOT");assertThat(reports.report(DAY,DAY,null,null).summary().cancelledBookings()).isEqualTo(1);}
    @Test void sameRevisionTimestampConflictDoesNotSilentlyRewrite(){var a=DemoSnapshots.forDay(DAY).getFirst();store.accept(a,false);assertThat(store.accept(changed(a,"status","CANCELLED"),false)).isEqualTo("SNAPSHOT_CONFLICT");assertThat(reports.report(DAY,DAY,null,null).summary().cancelledBookings()).isZero();}
    @Test void higherVersionWinsEvenWhenSourceClockMovesBackwards(){var q=DemoSnapshots.forDay(DAY).get(2);store.accept(q,false);var newer=changed(changed(q,"revision",2),"updatedAt",q.updatedAt().minusSeconds(2));assertThat(store.accept(newer,false)).isEqualTo("APPLIED");}
    @Test void restartAndRebuildPreserveExpectedMetrics(){fixture(false);var before=reports.report(DAY,DAY,null,null).summary();close();open();assertThat(store.rebuild(false,UUID.randomUUID())).isEqualTo(7);assertThat(reports.report(DAY,DAY,null,null).summary()).isEqualTo(before);assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics.operation_audit WHERE operation='REBUILD'",Integer.class)).isEqualTo(1);}
    @Test void fullReconciliationUsesPagedApiAndMarksOnlyCompleteSourcesSynced(){var all=DemoSnapshots.forDay(DAY);for(Source s:Source.values()){var items=all.stream().filter(v->v.source()==s).toList();when(sources.page(s,null)).thenReturn(new AnalyticsExportPage(items,null,Instant.now()));}reconciliation.run(UUID.randomUUID());assertThat(store.syncStates()).allSatisfy(s->assertThat(s.get("status")).isEqualTo("SYNCED"));assertThat(reports.report(DAY,DAY,null,null).summary().completed()).isEqualTo(2);}
    @Test void sourceOutageKeepsHistoricalProjectionAndReportsDegraded(){fixture(false);when(sources.page(any(),any())).thenThrow(new BusinessException(503,"Synthetic source outage"));reconciliation.run(null);assertThat(reports.report(DAY,DAY,null,null).summary().completed()).isEqualTo(2);assertThat(store.syncStates()).allSatisfy(s->assertThat(s.get("status")).isEqualTo("DEGRADED"));}
    @Test void missingSourceRecordDoesNotDeletePreviouslyObservedVisit(){var q=DemoSnapshots.forDay(DAY).get(2);store.accept(q,false);ingestion.commit(q.source(),UUID.randomUUID(),q.id(),Optional.empty());assertThat(reports.report(DAY,DAY,null,null).summary().arrived()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT outcome FROM analytics.event_receipts",String.class)).isEqualTo("SOURCE_RECORD_MISSING");}
    @Test void consultationBelongsToInitialVisitDayEvenAcrossMidnight(){fixture(false);var c=DemoSnapshots.forDay(DAY).get(5);c=changed(changed(changed(c,"businessDate",DAY.plusDays(1)),"revision",3),"updatedAt",c.updatedAt().plusSeconds(20));store.accept(c,false);assertThat(reports.report(DAY,DAY,null,null).summary().completed()).isEqualTo(2);assertThat(reports.report(DAY.plusDays(1),DAY.plusDays(1),null,null).summary().completed()).isZero();}
    @Test void reportFiltersUseSourceDepartmentAndRoom(){fixture(false);UUID dep=DemoSnapshots.forDay(DAY).getFirst().departmentId();assertThat(reports.report(DAY,DAY,dep,"DEMO-ROOM").summary().arrived()).isEqualTo(2);assertThat(reports.report(DAY,DAY,UUID.randomUUID(),null).summary().booked()).isZero();assertThat(reports.report(DAY,DAY,null,"OTHER-ROOM").summary().completed()).isZero();}
    @Test void sameDepartmentAndRoomStayInOneGroupWhenSourceOmitsDepartmentCode(){fixture(false);var q=DemoSnapshots.forDay(DAY).get(2);store.accept(changed(changed(q,"departmentCode",null),"revision",4),false);var report=reports.report(DAY,DAY,null,null);assertThat(report.groups()).hasSize(1);assertThat(report.groups().getFirst().metrics()).isEqualTo(report.summary());}
    @Test void failedSecondPageResumesCursorWithoutDoubleCounting(){var a=DemoSnapshots.forDay(DAY).getFirst();var b=DemoSnapshots.forDay(DAY).get(1);UUID cursor=a.id();when(sources.page(Source.APPOINTMENT,null)).thenReturn(new AnalyticsExportPage(List.of(a),cursor,Instant.now()));when(sources.page(Source.APPOINTMENT,cursor)).thenThrow(new BusinessException(503,"Synthetic interruption"));for(Source source:List.of(Source.QUEUE,Source.CONSULTATION))when(sources.page(source,null)).thenReturn(new AnalyticsExportPage(List.of(),null,Instant.now()));reconciliation.run(null);assertThat(jdbc.queryForObject("SELECT cursor_id FROM analytics.sync_state WHERE source='APPOINTMENT'",UUID.class)).isEqualTo(cursor);assertThat(reports.report(DAY,DAY,null,null).summary().booked()).isEqualTo(1);doReturn(new AnalyticsExportPage(List.of(b),null,Instant.now())).when(sources).page(Source.APPOINTMENT,cursor);reconciliation.run(null);assertThat(reports.report(DAY,DAY,null,null).summary().booked()).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT last_row_count FROM analytics.sync_state WHERE source='APPOINTMENT'",Long.class)).isEqualTo(2);}
    @Test void demoInitializerIsIdempotentAndRealModeNeverSeeds(){var real=new DataInitializer(store,false);real.run();assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics.source_projection",Integer.class)).isZero();var demo=new DataInitializer(store,true);demo.run();demo.run();assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics.source_projection WHERE synthetic=true",Integer.class)).isEqualTo(7);assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics.source_projection WHERE synthetic=false",Integer.class)).isZero();}
}
