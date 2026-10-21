package com.careflow.analytics;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.*;
@Component
public class DataInitializer implements CommandLineRunner {
    private final SnapshotStore store;private final boolean demo;
    public DataInitializer(SnapshotStore store,@Value("${analytics.demo-enabled:false}") boolean demo){this.store=store;this.demo=demo;}
    @Override public void run(String... args){if(demo)for(var value:DemoSnapshots.forDay(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))))store.accept(value,true);}
}
