package com.careflow.identity.dto;

import com.careflow.identity.domain.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull UserRole role) {
}
