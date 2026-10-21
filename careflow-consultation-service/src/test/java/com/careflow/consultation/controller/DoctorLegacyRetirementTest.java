package com.careflow.consultation.controller;

import com.careflow.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DoctorLegacyRetirementTest {
    @Test void legacyListDoesNotReturnHardcodedDoctors() {
        assertThatThrownBy(() -> new DoctorMockController().getAllDoctors())
                .isInstanceOf(BusinessException.class).hasMessageContaining("/api/directory/doctors");
    }
    @Test void unknownIdNeverFallsBackToTheFirstDoctor() {
        assertThatThrownBy(() -> new DoctorMockController().getDoctorById(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ngừng hỗ trợ");
    }
}
