package com.careflow.patient.client.dto;

import lombok.Data;

@Data
public class AssignmentAccessResponse {
    private boolean allowed;

    public static AssignmentAccessResponse allowed() {
        AssignmentAccessResponse response = new AssignmentAccessResponse();
        response.allowed = true;
        return response;
    }

    public static AssignmentAccessResponse denied() {
        return new AssignmentAccessResponse();
    }
}
