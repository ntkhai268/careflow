package com.careflow.directory.service;

import com.careflow.directory.dto.request.ServicePriceRequest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="PHASE1_CATALOG_JDBC",matches=".+")
class ServiceCatalogPostgresTest {
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static ServiceCatalogService service;
    @BeforeAll static void database() {
        String url=System.getenv("PHASE1_CATALOG_JDBC");
        if(!url.equals("jdbc:postgresql://127.0.0.1:15432/careflow_phase1_catalog_test")) throw new IllegalArgumentException("Use only the isolated local catalog test database");
        var ds=new DriverManagerDataSource(url,System.getenv("PHASE1_DB_USER"),System.getenv("PHASE1_DB_PASSWORD"));
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));service=new ServiceCatalogService(jdbc);
    }
    @BeforeEach void cleanFixture() { jdbc.execute("TRUNCATE medical_service_prices"); }
    ServicePriceRequest price(long version,long amount,boolean active,Instant effective) {
        return new ServicePriceRequest("GENERAL_CONSULTATION","Khám thường","Fixture",amount,15,active,effective,version);
    }
    @Test void demoSeedIsIdempotent() {
        tx.execute(s -> { service.seedDemo();service.seedDemo();return null; });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM medical_service_prices",Integer.class)).isEqualTo(1);
    }
    @Test void revisionIsAppendOnlyAndStaleWriteFails() {
        tx.execute(s -> service.publish(price(0,150000,true,null),UUID.randomUUID()));
        tx.execute(s -> service.publish(price(1,185000,true,null),UUID.randomUUID()));
        assertThat(service.current("GENERAL_CONSULTATION").amount()).isEqualTo(185000);
        assertThat(jdbc.queryForObject("SELECT amount FROM medical_service_prices WHERE version=1",Long.class)).isEqualTo(150000);
        assertThatThrownBy(() -> tx.execute(s -> service.publish(price(1,1,true,null),UUID.randomUUID()))).hasMessageContaining("đã thay đổi");
    }
    @Test void futureRevisionDoesNotReplaceCurrentQuoteEarly() {
        tx.execute(s -> service.publish(price(0,150000,true,null),UUID.randomUUID()));
        tx.execute(s -> service.publish(price(1,200000,true,Instant.now().plusSeconds(3600)),UUID.randomUUID()));
        assertThat(service.current("GENERAL_CONSULTATION").amount()).isEqualTo(150000);
        assertThat(service.list(false).getFirst().version()).isEqualTo(1);
        assertThat(service.list(true).getFirst().version()).isEqualTo(2);
    }
    @Test void inactiveLatestRevisionCannotExposePreviousActivePrice() {
        tx.execute(s -> service.publish(price(0,150000,true,null),UUID.randomUUID()));
        tx.execute(s -> service.publish(price(1,150000,false,null),UUID.randomUUID()));
        assertThat(service.list(false)).isEmpty();
        assertThatThrownBy(() -> service.current("GENERAL_CONSULTATION")).hasMessageContaining("ngừng nhận lịch");
    }
    @Test void concurrentPublishingHasExactlyOneWinner() throws Exception {
        tx.execute(s -> service.publish(price(0,150000,true,null),UUID.randomUUID()));
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task=() -> {start.await();try{tx.execute(s -> service.publish(price(1,180000,true,null),UUID.randomUUID()));return true;}catch(com.careflow.common.exception.BusinessException e){return false;}};
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM medical_service_prices",Integer.class)).isEqualTo(2);
    }
}
