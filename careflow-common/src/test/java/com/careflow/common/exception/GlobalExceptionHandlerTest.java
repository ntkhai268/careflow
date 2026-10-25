package com.careflow.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ConflictFixture())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"object", "generic"})
    void staleWritesReturnConflictWithoutPersistenceDetails(String kind) throws Exception {
        var result = mvc.perform(get("/conflict/" + kind))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andReturn();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("private-row-marker", "private-table-marker", "OptimisticLocking");
    }

    @Test
    void authorizationDenialKeepsItsStatus() throws Exception {
        mvc.perform(get("/conflict/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void unrelatedFailureIsNotReportedAsAWriteConflict() throws Exception {
        mvc.perform(get("/conflict/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }

    @RestController
    static class ConflictFixture {
        @GetMapping("/conflict/{kind}")
        public void write(@PathVariable String kind) {
            switch (kind) {
                case "object" -> throw new ObjectOptimisticLockingFailureException(
                        "private-table-marker", "private-row-marker");
                case "generic" -> throw new OptimisticLockingFailureException("private-row-marker");
                case "forbidden" -> throw new BusinessException(403, "Không có quyền.");
                default -> throw new IllegalStateException("private-table-marker");
            }
        }
    }
}
