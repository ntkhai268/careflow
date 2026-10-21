package com.careflow.appointment.service;

import com.careflow.appointment.client.*;
import com.careflow.appointment.dto.request.CreateAppointmentRequest;
import com.careflow.appointment.model.Appointment;
import com.careflow.appointment.repository.AppointmentRepository;
import com.careflow.common.dto.ApiResponse;
import org.junit.jupiter.api.*;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AppointmentPricingTest {
    AppointmentRepository repository;
    DirectoryClient directory;
    AppointmentService service;
    UUID owner;
    @BeforeEach void setup() {
        repository=mock(AppointmentRepository.class);
        directory=AppointmentPricingFixture.priced(null);
        service=new AppointmentService(repository,mock(AppointmentEventService.class),directory,null,null,mock(AppointmentConsultationClient.class));
        owner=UUID.randomUUID();
        lenient().when(repository.saveAndFlush(any())).thenAnswer(call -> { Appointment a=call.getArgument(0);a.setId(UUID.randomUUID());return a; });
    }
    CreateAppointmentRequest request(long version) {
        return CreateAppointmentRequest.builder().patientId(UUID.randomUUID()).patientName("Synthetic quote")
                .department("NOI_TONG_QUAT").appointmentDate(LocalDate.now().plusDays(30)).timeSlot("09:00-09:30")
                .serviceCode("GENERAL_CONSULTATION").priceVersion(version).build();
    }
    @Test void staleQuoteIsRejectedBeforeSaving() {
        assertThatThrownBy(() -> service.createAppointment(request(2),owner,"price",null)).hasMessageContaining("Bảng giá đã thay đổi");
        verify(repository,never()).saveAndFlush(any());
    }
    @Test void inactiveServiceCannotBeBooked() {
        when(directory.getServicePrice(anyString())).thenReturn(ApiResponse.success(AppointmentPricingFixture.price("GENERAL_CONSULTATION",2,200000,false)));
        assertThatThrownBy(() -> service.createAppointment(request(2),owner,"price",null)).hasMessageContaining("chưa sẵn sàng");
        verify(repository,never()).saveAndFlush(any());
    }
    @Test void missingDirectoryPriceNeverFallsBackToLocalAmount() {
        when(directory.getServicePrice(anyString())).thenReturn(null);
        assertThatThrownBy(() -> service.createAppointment(request(1),owner,"price",null)).hasMessageContaining("chưa sẵn sàng");
        verify(repository,never()).saveAndFlush(any());
    }
    @Test void newPricesDoNotRewriteExistingSnapshots() {
        var original=service.createAppointment(request(1),owner,"price",null);
        when(directory.getServicePrice(anyString())).thenReturn(ApiResponse.success(AppointmentPricingFixture.price("GENERAL_CONSULTATION",2,215000,true)));
        var later=service.createAppointment(request(2),owner,"price",null);
        assertThat(original.getQuotedAmount()).isEqualTo(150000L);
        assertThat(original.getPriceVersion()).isEqualTo(1);
        assertThat(later.getQuotedAmount()).isEqualTo(215000L);
        assertThat(later.getPriceVersion()).isEqualTo(2);
    }
    @Test void retryRetainsCommittedQuoteAndChangedPayloadIsRejected() {
        var request=request(1);
        var original=service.createAppointment(request,owner,"price","same-key");
        when(directory.getServicePrice(anyString())).thenReturn(ApiResponse.success(AppointmentPricingFixture.price("GENERAL_CONSULTATION",2,215000,true)));
        assertThat(service.createAppointment(request,owner,"price","same-key").getId()).isEqualTo(original.getId());
        request.setPriceVersion(2L);
        assertThatThrownBy(() -> service.createAppointment(request,owner,"price","same-key")).hasMessageContaining("Idempotency-Key");
        verify(repository).saveAndFlush(any());
    }
}
