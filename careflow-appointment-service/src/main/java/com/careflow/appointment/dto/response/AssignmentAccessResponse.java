package com.careflow.appointment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentAccessResponse {
    private boolean allowed;

    public static AssignmentAccessResponse allowed() {
        return new AssignmentAccessResponse(true);
    }

    public static AssignmentAccessResponse denied() {
        return new AssignmentAccessResponse(false);
    }
}
