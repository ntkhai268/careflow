package com.careflow.directory.controller;

import com.careflow.common.exception.GlobalExceptionHandler;
import com.careflow.directory.service.DirectoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DepartmentControllerAuthTest {

    @Mock
    private DirectoryService directoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DepartmentController(directoryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createDepartmentRequiresAdminRole() throws Exception {
        mockMvc.perform(post("/api/directory/departments")
                        .contentType("application/json")
                        .content("{\"code\":\"CARDIO\",\"name\":\"Cardiology\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createDepartmentRejectsRoleContainingAdminInsteadOfExactAdmin() throws Exception {
        mockMvc.perform(post("/api/directory/departments")
                        .header("X-User-Role", "NOT_ADMIN")
                        .contentType("application/json")
                        .content("{\"code\":\"CARDIO\",\"name\":\"Cardiology\"}"))
                .andExpect(status().isForbidden());
    }
}
