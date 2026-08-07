package com.careflow.lab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.careflow")
@EnableFeignClients(basePackages = "com.careflow.lab.client")
public class LabApplication {
    public static void main(String[] args) {
        SpringApplication.run(LabApplication.class, args);
    }
}
