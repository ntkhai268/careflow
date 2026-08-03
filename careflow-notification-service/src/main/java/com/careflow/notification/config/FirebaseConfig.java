package com.careflow.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.careflow.notification.service.PushSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@EnableConfigurationProperties(FirebasePushProperties.class)
public class FirebaseConfig {
    @Bean
    @ConditionalOnProperty(prefix = "notification.push.firebase", name = "enabled", havingValue = "true")
    FirebaseApp firebaseApp(FirebasePushProperties properties) throws IOException {
        FirebaseOptions.Builder options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault());
        if (properties.getProjectId() != null && !properties.getProjectId().isBlank()) {
            options.setProjectId(properties.getProjectId());
        }
        return FirebaseApp.getApps().stream().findFirst()
                .orElseGet(() -> FirebaseApp.initializeApp(options.build()));
    }

    @Bean
    @ConditionalOnProperty(prefix = "notification.push.firebase", name = "enabled", havingValue = "true")
    FirebaseMessaging firebaseMessaging(FirebaseApp app) {
        return FirebaseMessaging.getInstance(app);
    }

    @Bean
    @ConditionalOnProperty(prefix = "notification.push.firebase", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    PushSender disabledPushSender() {
        return (token, notification) -> { };
    }
}
