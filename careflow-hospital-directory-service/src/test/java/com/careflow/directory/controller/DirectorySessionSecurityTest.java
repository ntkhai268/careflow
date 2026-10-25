package com.careflow.directory.controller;

import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.GlobalExceptionHandler;
import com.careflow.common.security.ActiveSessionClient;
import com.careflow.common.security.ActiveSessionFilter;
import com.careflow.directory.service.DirectoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DirectorySessionSecurityTest {
    @Mock DirectoryService directory;
    @Mock ActiveSessionClient sessions;
    private MockMvc mvc;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new StaffAssignmentController(directory), new DepartmentController(directory))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(new ActiveSessionFilter(sessions, new ObjectMapper().findAndRegisterModules()))
                .build();
    }

    private void authenticated(String role) {
        when(sessions.verifyRequest(eq("Bearer valid"), anyString(), any(), any(), any(), any(), any())).thenReturn(
                new ActiveSessionClient.Principal(true, userId, role, UUID.randomUUID(), Instant.now().plusSeconds(60)));
    }

    @Test
    void publicCatalogDoesNotRequireAuthority() throws Exception {
        when(directory.getAllDepartments()).thenReturn(List.of());
        mvc.perform(get("/api/directory/departments")).andExpect(status().isOk());
        verifyNoInteractions(sessions);
    }

    @Test
    void rejectedSessionCannotUseForgedAdminHeaders() throws Exception {
        when(sessions.verifyRequest(eq("Bearer revoked"), anyString(), any(), any(), any(), any(), any())).thenThrow(new BusinessException(401, "Revoked"));
        mvc.perform(get("/api/directory/staff/assignments").header("Authorization", "Bearer revoked")
                .header("X-User-Role", "ADMIN")).andExpect(status().isUnauthorized());
        verifyNoInteractions(directory);
    }

    @Test
    void patientCannotSpoofAdminForAssignmentList() throws Exception {
        authenticated("PATIENT");
        mvc.perform(get("/api/directory/staff/assignments").header("Authorization", "Bearer valid")
                .header("X-User-Role", "ADMIN")).andExpect(status().isForbidden());
        verifyNoInteractions(directory);
    }

    @Test
    void adminCanInspectAssignments() throws Exception {
        authenticated("ADMIN");
        when(directory.getStaffAssignments(null, null)).thenReturn(List.of());
        mvc.perform(get("/api/directory/staff/assignments").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk());
    }

    @Test
    void staffCanCheckOwnRoomAssignment() throws Exception {
        authenticated("STAFF");
        when(directory.hasStaffRoomAccess(userId, "ROOM-01")).thenReturn(true);
        mvc.perform(get("/api/directory/staff/" + userId + "/room-access?roomId=ROOM-01")
                .header("Authorization", "Bearer valid")).andExpect(status().isOk());
    }

    @Test
    void staffCannotInspectOtherStaffWithForgedUserHeader() throws Exception {
        authenticated("STAFF");
        UUID other = UUID.randomUUID();
        mvc.perform(get("/api/directory/staff/" + other + "/room-access?roomId=ROOM-01")
                .header("Authorization", "Bearer valid").header("X-User-Id", other))
                .andExpect(status().isForbidden());
        verifyNoInteractions(directory);
    }

    @Test
    void adminCanCheckAnotherStaffRoomAssignment() throws Exception {
        authenticated("ADMIN");
        UUID other = UUID.randomUUID();
        when(directory.hasStaffRoomAccess(other, "ROOM-01")).thenReturn(true);
        mvc.perform(get("/api/directory/staff/" + other + "/room-access?roomId=ROOM-01")
                .header("Authorization", "Bearer valid")).andExpect(status().isOk());
    }

    @Test
    void authorityFailureStopsProtectedRead() throws Exception {
        when(sessions.verifyRequest(eq("Bearer valid"), anyString(), any(), any(), any(), any(), any())).thenThrow(new BusinessException(503, "Unavailable"));
        mvc.perform(get("/api/directory/staff/assignments").header("Authorization", "Bearer valid"))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(directory);
    }
}
