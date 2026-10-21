package com.careflow.identity.recovery;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;

@Configuration @EnableScheduling
public class RecoveryMailConfiguration {
    @Bean Clock recoveryClock() { return Clock.systemUTC(); }
}
