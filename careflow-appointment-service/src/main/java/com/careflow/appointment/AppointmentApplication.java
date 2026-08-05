package com.careflow.appointment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@ComponentScan(basePackages = {"com.careflow.appointment", "com.careflow.common"})
@EnableJpaRepositories(basePackages = "com.careflow.appointment.repository")
@EntityScan(basePackages = {"com.careflow.appointment.model", "com.careflow.common.model"})
@EnableScheduling
@EnableFeignClients(basePackages = "com.careflow.appointment.client")
public class AppointmentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AppointmentApplication.class, args);
    }
}
