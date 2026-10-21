package com.careflow.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
public class ProductionSafetyAutoConfiguration {
    @Bean
    public Object productionSafetyChecks(Environment environment) {
        if(environment.matchesProfiles("prod","production")) {
            if(!environment.matchesProfiles("secure")) throw new IllegalStateException("Production requires the secure profile");
            if(environment.matchesProfiles("demo","test")) throw new IllegalStateException("Demo/test data is forbidden in production");
            String database=environment.getProperty("spring.datasource.url","");
            if(!database.isBlank() && !database.contains("sslmode=verify-full"))
                throw new IllegalStateException("Production PostgreSQL requires verified TLS (sslmode=verify-full)");
            if("identity-service".equals(environment.getProperty("spring.application.name"))) {
                if(!environment.getProperty("security.auth.require-staff-passkey",Boolean.class,false))
                    throw new IllegalStateException("Production staff authentication requires passkeys");
                if(environment.getProperty("security.auth.public-registration-enabled",Boolean.class,true))
                    throw new IllegalStateException("Unverified public registration is forbidden in production");
            }
        }
        return new Object();
    }
}
