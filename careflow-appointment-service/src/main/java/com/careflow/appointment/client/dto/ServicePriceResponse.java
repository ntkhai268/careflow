package com.careflow.appointment.client.dto;

import java.time.Instant;

public record ServicePriceResponse(String code,String name,String description,long amount,String currency,
        long version,Instant effectiveAt,boolean active,int estimatedMinutes) {}
