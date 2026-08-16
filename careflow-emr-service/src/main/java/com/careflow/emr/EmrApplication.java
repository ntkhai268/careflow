package com.careflow.emr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class EmrApplication {
    public static void main(String[] args) {
        SpringApplication.run(EmrApplication.class, args);
    }
}
