package com.careflow.common.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ApiResponseTest {
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void successAndCreatedFactoriesUseExpectedContract() throws Exception {
        ApiResponse<String> success = ApiResponse.success("done");
        ApiResponse<String> created = ApiResponse.created("Created", "id-1");

        assertThat(success.getStatus()).isEqualTo(200);
        assertThat(created.getStatus()).isEqualTo(201);
        assertThat(objectMapper.writeValueAsString(success))
                .contains("\"timestamp\":\"")
                .contains("\"data\":\"done\"");
    }

    @Test
    void invalidStatusAndNonErrorStatusAreRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ApiResponse.of(99, "invalid", null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ApiResponse.error(200, "not an error"));
    }
}
