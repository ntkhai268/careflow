package com.careflow.lab.service;

import com.careflow.common.event.EventEnvelope;
import com.careflow.common.exception.BusinessException;
import com.careflow.lab.dto.request.CreateLabOrderRequest;
import com.careflow.lab.dto.request.LabOrderItemRequest;
import com.careflow.lab.dto.request.UpdateLabResultRequest;
import com.careflow.lab.model.LabOrder;
import com.careflow.lab.model.LabOrderItem;
import com.careflow.lab.model.LabOrderItemStatus;
import com.careflow.lab.model.LabOrderStatus;
import com.careflow.lab.repository.LabOrderItemRepository;
import com.careflow.lab.repository.LabOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabOrderServiceTest {
    @Mock LabOrderRepository orders;
    @Mock LabOrderItemRepository items;
    @Mock LabEventPublisher events;

    private LabOrderService service;
    private UUID doctorId;
    private UUID patientId;
    private UUID consultationId;

    @BeforeEach
    void setUp() {
        service = new LabOrderService(orders, items, events);
        doctorId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        consultationId = UUID.randomUUID();
    }

    @Test
    void createOrderUsesGatewayActorAndPublishesReadyEnvelopeWithQueueContext() {
        CreateLabOrderRequest request = new CreateLabOrderRequest(
                consultationId,
                patientId,
                List.of(new LabOrderItemRequest(
                        "CBC", "Complete blood count", "LAB-HEMATOLOGY-01", true, "No fasting")),
                "Rule out infection",
                false,
                null);
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createOrder(request, doctorId, "DOCTOR", "trace-lab-1");

        assertThat(response.status()).isEqualTo(LabOrderStatus.ORDERED);
        assertThat(response.orderedByDoctorId()).isEqualTo(doctorId);
        ArgumentCaptor<EventEnvelope> event = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(events).publishReadyForExecution(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo("LabOrderReadyForExecution");
        assertThat(event.getValue().correlationId()).isEqualTo("trace-lab-1");
        assertThat(event.getValue().aggregateId()).isEqualTo(response.id());
        assertThat(event.getValue().payload().get("labOrderId").asText()).isEqualTo(response.id().toString());
        assertThat(event.getValue().payload().get("patientId").asText()).isEqualTo(patientId.toString());
        assertThat(event.getValue().payload().get("consultationId").asText()).isEqualTo(consultationId.toString());
        assertThat(event.getValue().payload().get("servicePoints").get(0).get("servicePointId").asText())
                .isEqualTo("LAB-HEMATOLOGY-01");
    }

    @Test
    void createOrderDoesNotTrustClientSuppliedDoctorId() {
        CreateLabOrderRequest request = new CreateLabOrderRequest(
                consultationId,
                patientId,
                List.of(new LabOrderItemRequest(
                        "GLUCOSE", "Glucose", "LAB-BIOCHEM-01", true, null)),
                null,
                false,
                UUID.randomUUID());
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createOrder(request, doctorId, "DOCTOR", "trace-lab-2");

        assertThat(response.orderedByDoctorId()).isEqualTo(doctorId);
        assertThat(response.orderedByDoctorId()).isNotEqualTo(request.actorId());
    }

    @Test
    void laboratoryOrderDoesNotAcceptPaymentWorkflow() {
        CreateLabOrderRequest request = new CreateLabOrderRequest(
                consultationId,
                patientId,
                List.of(new LabOrderItemRequest("CBC", "Complete blood count", "LAB-HEMATOLOGY-01", true, null)),
                null,
                true,
                doctorId);

        assertThatThrownBy(() -> service.createOrder(request, doctorId, "DOCTOR", "trace-lab-payment"))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(400);
        verifyNoInteractions(orders, events);
    }

    @Test
    void patientReadsAreRestrictedToTheirOwnPatientId() {
        assertThatThrownBy(() -> service.getByPatient(patientId, UUID.randomUUID(), "PATIENT"))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(403);

        verifyNoInteractions(orders);
    }

    @Test
    void orderMustBeStartedBeforeRequiredResultsCanBeFinalized() {
        LabOrder order = orderWithItem(true);
        order.setStatus(LabOrderStatus.ORDERED);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.finalizeOrder(order.getId(), doctorId, "DOCTOR", "trace-lab-3"))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(409);

        verify(events, never()).publishAllRequiredResultsAvailable(any());
    }

    @Test
    void finalizeRequiresEveryRequiredItemToHaveAResult() {
        LabOrder order = orderWithItem(true);
        order.setStatus(LabOrderStatus.IN_PROGRESS);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.finalizeOrder(order.getId(), doctorId, "DOCTOR", "trace-lab-4"))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(409);

        verify(events, never()).publishAllRequiredResultsAvailable(any());
    }

    @Test
    void finalizingAllRequiredResultsPublishesResultReviewEnvelopeOnce() {
        LabOrder order = orderWithItem(true);
        order.setStatus(LabOrderStatus.IN_PROGRESS);
        LabOrderItem item = order.getItems().getFirst();
        item.setStatus(LabOrderItemStatus.COMPLETED);
        item.setResultValue("13.4");
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.finalizeOrder(order.getId(), doctorId, "DOCTOR", "trace-lab-5");
        service.finalizeOrder(order.getId(), doctorId, "DOCTOR", "trace-lab-5-redelivery");

        assertThat(order.getStatus()).isEqualTo(LabOrderStatus.RESULT_AVAILABLE);
        verify(events, times(1)).publishAllRequiredResultsAvailable(any());
    }

    @Test
    void resultSubmissionRequiresStartedOrderAndStoresGatewayStaffActor() {
        LabOrder order = orderWithItem(true);
        order.setStatus(LabOrderStatus.IN_PROGRESS);
        LabOrderItem item = order.getItems().getFirst();
        UUID staffId = UUID.randomUUID();
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(items.findById(item.getId())).thenReturn(Optional.of(item));
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateResult(order.getId(), item.getId(),
                new UpdateLabResultRequest("13.4", "12-16", "g/dL", "NORMAL", "verified"),
                staffId, "LAB_TECHNICIAN");

        assertThat(item.getStatus()).isEqualTo(LabOrderItemStatus.COMPLETED);
        assertThat(item.getResultValue()).isEqualTo("13.4");
        assertThat(item.getPerformedByStaffId()).isEqualTo(staffId);
    }

    private LabOrder orderWithItem(boolean required) {
        LabOrder order = new LabOrder();
        order.setId(UUID.randomUUID());
        order.setConsultationId(consultationId);
        order.setPatientId(patientId);
        order.setOrderedByDoctorId(doctorId);
        order.setStatus(LabOrderStatus.ORDERED);

        LabOrderItem item = new LabOrderItem();
        item.setId(UUID.randomUUID());
        item.setOrder(order);
        item.setServiceCode("CBC");
        item.setServiceName("Complete blood count");
        item.setServicePointId("LAB-HEMATOLOGY-01");
        item.setRequired(required);
        item.setStatus(LabOrderItemStatus.ORDERED);
        order.getItems().add(item);
        return order;
    }
}
