package com.careflow.directory.config;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
@Component @RequiredArgsConstructor
public class HandoverBookingInitializer implements CommandLineRunner {
    private final DataSource dataSource;
    public void run(String... args) { new ResourceDatabasePopulator(new ClassPathResource("db/handover-booking-primary.sql")).execute(dataSource); }
}
