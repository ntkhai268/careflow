package com.careflow.prescription;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.careflow")
public class PrescriptionApplication {
    public static void main(String[] args) {
        SpringApplication.run(PrescriptionApplication.class, args);
    }
}
