package com.careflow.ai.voice;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("careflow.voice")
public class VoiceProperties {
    private boolean enabled;
    private String jdbcUrl, dbUser, dbPassword, identityUrl, consultationUrl, prescriptionUrl, workerUrl;
    private String contentKey = "";
    private String workerToken = "", callerName = "ai-service", callerKey = "";
    private boolean allowLoopbackHttp;
    private int queueCapacity = 8, perDoctorCapacity = 2, ttlSeconds = 900;
    private int dependencyTimeoutSeconds = 5, inferenceTimeoutSeconds = 210;
}
