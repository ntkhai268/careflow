package com.careflow.lab.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Idempotent master data only; never resets existing orders or queues. */
@Component
public class DataInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    public DataInitializer(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override
    public void run(String... args) {
        seed("CBC","Công thức máu toàn phần (CBC)","LAB-HEMATOLOGY-01","Không cần nhịn ăn");
        seed("GLUCOSE","Định lượng Glucose máu","LAB-BIOCHEM-01","Theo hướng dẫn chuẩn bị của cơ sở xét nghiệm");
    }
    private void seed(String code,String name,String point,String preparation) {
        jdbc.update("INSERT INTO lab_service_catalog(service_code,service_name,service_point_id,preparation_instruction) VALUES (?,?,?,?) ON CONFLICT(service_code) DO NOTHING",code,name,point,preparation);
    }
}
