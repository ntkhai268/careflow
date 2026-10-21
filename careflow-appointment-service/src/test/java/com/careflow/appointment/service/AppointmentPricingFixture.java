package com.careflow.appointment.service;

import com.careflow.appointment.client.DirectoryClient;
import com.careflow.appointment.client.dto.*;
import com.careflow.appointment.model.Department;
import com.careflow.common.dto.ApiResponse;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Explicit mocked Directory contract, never packaged into the runtime artifact. */
public final class AppointmentPricingFixture {
    public static DirectoryClient priced(DirectoryClient existing) {
        DirectoryClient client=existing==null?mock(DirectoryClient.class):existing;
        lenient().when(client.getServicePrice(anyString())).thenAnswer(call -> ApiResponse.success(price(call.getArgument(0),1,150000,true)));
        if(existing==null) {
            lenient().when(client.getDepartmentByCode(anyString())).thenAnswer(call -> {
                Department d=Department.fromCode(call.getArgument(0));
                return ApiResponse.success(DepartmentResponse.builder().code(d.name()).id(d.getId()).name(d.getDisplayName()).isActive(true).build());
            });
            lenient().when(client.getAllRooms()).thenReturn(ApiResponse.success(Arrays.stream(Department.values()).map(d ->
                    RoomResponse.builder().id(d.getRoomId()).departmentCode(d.name()).displayName(d.getRoomDisplayName()).roomType("CONSULTATION").isActive(true).build()).toList()));
            lenient().when(client.getDoctors(anyString())).thenAnswer(call -> {
                Department d=Department.fromCode(call.getArgument(0));
                return ApiResponse.success(List.of(DoctorProfileResponse.builder().userId(UUID.nameUUIDFromBytes(d.name().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .fullName("Synthetic assigned doctor").departmentCode(d.name()).assignedRoomId(d.getRoomId()).isActive(true).primaryForBooking(true).build()));
            });
        }
        return client;
    }
    static ServicePriceResponse price(String code,long version,long amount,boolean active) {
        return new ServicePriceResponse(code,"Khám thường","Test contract",amount,"VND",version,Instant.parse("2026-01-01T00:00:00Z"),active,15);
    }
}
