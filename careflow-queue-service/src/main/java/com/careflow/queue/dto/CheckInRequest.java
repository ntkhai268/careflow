package com.careflow.queue.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckInRequest(@NotBlank String qrToken) {
}
