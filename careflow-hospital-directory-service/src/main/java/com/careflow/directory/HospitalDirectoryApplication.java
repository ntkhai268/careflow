package com.careflow.directory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.careflow.directory", "com.careflow.common"})
public class HospitalDirectoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(HospitalDirectoryApplication.class, args);
    }
}
