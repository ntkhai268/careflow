package com.careflow.directory;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ComponentScan;

import static org.assertj.core.api.Assertions.assertThat;

class HospitalDirectoryApplicationTest {

    @Test
    void scansCommonComponentsSoBusinessErrorsUseApiExceptionHandler() {
        var componentScan = HospitalDirectoryApplication.class.getAnnotation(ComponentScan.class);

        assertThat(componentScan).isNotNull();
        assertThat(componentScan.basePackages())
                .containsExactlyInAnyOrder("com.careflow.directory", "com.careflow.common");
    }
}
