package com.careflow.analytics;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication(scanBasePackages={"com.careflow.analytics","com.careflow.common.exception"})
@EnableScheduling
public class AnalyticsApplication {
    public static void main(String[] args){SpringApplication.run(AnalyticsApplication.class,args);}
}
