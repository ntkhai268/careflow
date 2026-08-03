package com.careflow.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification;MODE=PostgreSQL;INIT=CREATE SCHEMA IF NOT EXISTS notification",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "eureka.client.enabled=false",
        "notification.push.firebase.enabled=false"
})
class NotificationApplicationTest {
    @Test
    void startsWithoutFirebaseCredentialsWhenPushIsDisabled() {
    }
}
