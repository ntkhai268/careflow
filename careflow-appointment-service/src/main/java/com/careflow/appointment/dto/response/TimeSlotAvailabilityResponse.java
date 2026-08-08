package com.careflow.appointment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSlotAvailabilityResponse {
    private String timeSlot;
    private int bookedCount;
    private int capacity;
    private int remaining;
    private boolean available;
    private String unavailableReason;
}
