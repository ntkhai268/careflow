package com.careflow.patient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.careflow.patient", "com.careflow.common"})
public class PatientApplication {
    public static void main(String[] args) {
        SpringApplication.run(PatientApplication.class, args);
    }
}
