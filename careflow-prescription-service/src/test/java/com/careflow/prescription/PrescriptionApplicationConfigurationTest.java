package com.careflow.prescription;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

class PrescriptionApplicationConfigurationTest {

    @Test
    void componentScanIncludesSharedCareflowComponents() {
        SpringBootApplication application = PrescriptionApplication.class
                .getAnnotation(SpringBootApplication.class);

        assertThat(application.scanBasePackages())
                .contains("com.careflow");
    }
}
