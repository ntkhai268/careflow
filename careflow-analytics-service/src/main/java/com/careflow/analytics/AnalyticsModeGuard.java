package com.careflow.analytics;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
@Component
public class AnalyticsModeGuard {
    public AnalyticsModeGuard(Environment environment,@Value("${analytics.demo-enabled:false}") boolean demo){
        if(demo&&environment.matchesProfiles("prod","production"))throw new IllegalStateException("Synthetic Analytics is forbidden in production");
    }
}
