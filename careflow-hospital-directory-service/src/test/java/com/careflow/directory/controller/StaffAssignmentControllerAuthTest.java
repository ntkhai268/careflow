package com.careflow.directory.controller;

import com.careflow.common.exception.GlobalExceptionHandler;
import com.careflow.directory.dto.StaffAssignmentResponse;
import com.careflow.directory.service.DirectoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StaffAssignmentControllerAuthTest {
    @Mock
    private DirectoryService directoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StaffAssignmentController(directoryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void assignmentListingRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/directory/staff/assignments"))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignmentCreationRequiresExactAdminRole() throws Exception {
        mockMvc.perform(post("/api/directory/staff/assignments")
                        .header("X-User-Role", "NOT_ADMIN")
                        .contentType("application/json")
                        .content("{\"userId\":\"55555555-5555-5555-5555-555555555555\",\"roomId\":\"ROOM-01\",\"departmentCode\":\"NOI_TONG_QUAT\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListAssignments() throws Exception {
        when(directoryService.getStaffAssignments(null, null)).thenReturn(List.of(
                StaffAssignmentResponse.builder()
                        .id(UUID.randomUUID())
                        .userId(UUID.fromString("55555555-5555-5555-5555-555555555555"))
                        .roomId("ROOM-01")
                        .departmentCode("NOI_TONG_QUAT")
                        .isActive(true)
                        .build()));

        mockMvc.perform(get("/api/directory/staff/assignments")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }
}
