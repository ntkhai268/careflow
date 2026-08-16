package com.careflow.queue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.careflow")
@EnableScheduling
@EnableFeignClients(basePackages = "com.careflow.queue.client")
public class QueueApplication {
    public static void main(String[] args) {
        SpringApplication.run(QueueApplication.class, args);
    }
}
