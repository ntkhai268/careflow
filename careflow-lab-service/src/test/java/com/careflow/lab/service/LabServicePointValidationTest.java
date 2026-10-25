package com.careflow.lab.service;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.lab.client.*;
import com.careflow.lab.dto.request.LabOrderItemRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LabServicePointValidationTest {
    private final LabDirectoryClient directory = mock(LabDirectoryClient.class);
    private final LabAccessService access = new LabAccessService(mock(LabConsultationClient.class),
            mock(PatientIdentityClient.class), mock(QueueExecutionClient.class), directory);

    private String fixture(String suffix) throws Exception {
        try (var input = new ClassPathResource("contracts/directory/access-" + suffix + ".json").getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<LabOrderItemRequest> items(String point) {
        return List.of(new LabOrderItemRequest("CBC", "Synthetic test", point, true, null));
    }

    private void catalog() throws Exception {
        when(directory.getRooms()).thenReturn(new ObjectMapper().readValue(fixture("success"), new TypeReference<>() {}));
    }

    private void denied(int status, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getStatus()).isEqualTo(status));
    }

    @Test
    void normalizesPointAndRequiresRealActiveLabCatalog() throws Exception {
        catalog();
        access.requireServicePoints(items(" lab-hematology-01 "));
        verify(directory).getRooms();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PHARMACY-MAIN-01", "LAB-INACTIVE", "UNKNOWN-POINT"})
    void nonLabInactiveAndUnknownPointsAreRejected(String point) throws Exception {
        catalog();
        denied(422, () -> access.requireServicePoints(items(point)));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 500, 503})
    void catalogErrorsCannotAuthorizeCreate(int status) throws Exception {
        var request = Request.create(Request.HttpMethod.GET, "http://directory.test/api/directory/rooms", Map.of(), null, StandardCharsets.UTF_8, null);
        when(directory.getRooms()).thenThrow(FeignException.errorStatus("getRooms", Response.builder().request(request)
                .status(status).headers(Map.of()).body(fixture("error-" + status), StandardCharsets.UTF_8).build()));
        denied(503, () -> access.requireServicePoints(items("LAB-HEMATOLOGY-01")));
    }

    @Test
    void incompleteCatalogIsUnavailable() {
        when(directory.getRooms()).thenReturn(ApiResponse.success(List.of(new LabDirectoryClient.Room("LAB-HEMATOLOGY-01", "LAB", null))));
        denied(503, () -> access.requireServicePoints(items("LAB-HEMATOLOGY-01")));
    }
}
