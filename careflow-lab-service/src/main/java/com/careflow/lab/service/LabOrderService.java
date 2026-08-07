package com.careflow.lab.service;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.dto.ApiResponse;
import com.careflow.common.event.EventEnvelope;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.lab.dto.request.CreateLabOrderRequest;
import com.careflow.lab.dto.request.LabOrderItemRequest;
import com.careflow.lab.dto.request.UpdateLabResultRequest;
import com.careflow.lab.dto.response.LabOrderItemResponse;
import com.careflow.lab.dto.response.LabOrderResponse;
import com.careflow.lab.client.PatientIdentityClient;
import com.careflow.lab.client.QueueExecutionClient;
import com.careflow.lab.model.*;
import com.careflow.lab.repository.LabOrderItemRepository;
import com.careflow.lab.repository.LabOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class LabOrderService {
    private static final String PRODUCER = "lab-service";
    private static final Set<String> CLINICAL_COMMAND_ROLES = Set.of(
            AppConstants.ROLE_DOCTOR, AppConstants.ROLE_STAFF, AppConstants.ROLE_ADMIN);
    private static final Set<String> LAB_COMMAND_ROLES = Set.of(
            AppConstants.ROLE_LAB_TECHNICIAN, AppConstants.ROLE_STAFF, AppConstants.ROLE_ADMIN);
    private static final Set<String> RESULT_FINALIZE_ROLES = Set.of(
            AppConstants.ROLE_LAB_TECHNICIAN, AppConstants.ROLE_STAFF,
            AppConstants.ROLE_DOCTOR, AppConstants.ROLE_ADMIN);

    private final LabOrderRepository orders;
    private final LabOrderItemRepository items;
    private final LabEventPublisher events;
    private final ObjectMapper objectMapper;
    private final PatientIdentityClient patientIdentityClient;
    private final QueueExecutionClient queueExecutionClient;

    LabOrderService(LabOrderRepository orders, LabOrderItemRepository items, LabEventPublisher events) {
        this(orders, items, events, new ObjectMapper().findAndRegisterModules(), null, null);
    }

    @Autowired
    public LabOrderService(LabOrderRepository orders, LabOrderItemRepository items,
                           LabEventPublisher events, ObjectMapper objectMapper,
                           PatientIdentityClient patientIdentityClient,
                           QueueExecutionClient queueExecutionClient) {
        this.orders = orders;
        this.items = items;
        this.events = events;
        this.objectMapper = objectMapper;
        this.patientIdentityClient = patientIdentityClient;
        this.queueExecutionClient = queueExecutionClient;
    }

    @Transactional
    public LabOrderResponse createOrder(CreateLabOrderRequest request, UUID actorUserId,
                                        String actorRole, String correlationId) {
        requireRole(actorRole, CLINICAL_COMMAND_ROLES);
        requireActor(actorUserId);
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(400, "Lab order must contain at least one item");
        }
        if (Boolean.TRUE.equals(request.paymentRequired())) {
            throw new BusinessException(400, "Laboratory orders do not support a payment workflow");
        }

        LabOrder order = new LabOrder();
        order.setId(UUID.randomUUID());
        order.setConsultationId(request.consultationId());
        order.setPatientId(request.patientId());
        order.setOrderedByDoctorId(actorUserId);
        order.setDepartmentId(request.departmentId());
        order.setClinicalNote(trimToNull(request.clinicalNote()));
        order.setStatus(LabOrderStatus.ORDERED);
        order.setPaymentStatus(PaymentStatus.NOT_REQUIRED);
        for (LabOrderItemRequest itemRequest : request.items()) {
            LabOrderItem item = new LabOrderItem();
            item.setId(UUID.randomUUID());
            item.setServiceCode(requireText(itemRequest.serviceCode(), "serviceCode"));
            item.setServiceName(requireText(itemRequest.serviceName(), "serviceName"));
            item.setServicePointId(requireText(itemRequest.servicePointId(), "servicePointId")
                    .toUpperCase(Locale.ROOT));
            item.setRequired(itemRequest.required() == null || itemRequest.required());
            item.setStatus(LabOrderItemStatus.ORDERED);
            order.addItem(item);
        }

        LabOrder saved = orders.save(order);
        events.publishReadyForExecution(readyEnvelope(saved, correlationId));
        return LabOrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public LabOrderResponse getOrder(UUID id, UUID actorUserId, String actorRole) {
        LabOrder order = requireOrder(id);
        enforceReadAccess(order, actorUserId, actorRole);
        return LabOrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<LabOrderResponse> getByConsultation(UUID consultationId, UUID actorUserId, String actorRole) {
        UUID ownPatientId = patientScope(actorUserId, actorRole);
        return orders.findByConsultationIdOrderByCreatedAtDesc(consultationId).stream()
                .filter(order -> canRead(order, ownPatientId, actorRole))
                .map(LabOrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LabOrderResponse> getByPatient(UUID patientId, UUID actorUserId, String actorRole) {
        UUID ownPatientId = patientScope(actorUserId, actorRole);
        if (isPatient(actorRole) && !patientId.equals(ownPatientId)) {
            throw new BusinessException(403, "Cannot read another patient's lab orders");
        }
        return orders.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
                .map(LabOrderResponse::from)
                .toList();
    }

    @Transactional
    public LabOrderResponse startOrder(UUID id, UUID actorUserId, String actorRole) {
        requireRole(actorRole, LAB_COMMAND_ROLES);
        requireActor(actorUserId);
        LabOrder order = requireOrder(id);
        if (order.getStatus() == LabOrderStatus.IN_PROGRESS) return LabOrderResponse.from(order);
        if (order.getStatus() != LabOrderStatus.ORDERED && order.getStatus() != LabOrderStatus.QUEUED
                && order.getStatus() != LabOrderStatus.CALLED) {
            throw invalidTransition(order, LabOrderStatus.IN_PROGRESS);
        }
        verifyLabQueueEntryIsInProgress(id, actorUserId, actorRole);
        order.setStatus(LabOrderStatus.IN_PROGRESS);
        return LabOrderResponse.from(orders.save(order));
    }

    @Transactional
    public LabOrderItemResponse updateResult(UUID orderId, UUID itemId, UpdateLabResultRequest request,
                                             UUID actorUserId, String actorRole) {
        requireRole(actorRole, LAB_COMMAND_ROLES);
        requireActor(actorUserId);
        LabOrder order = requireOrder(orderId);
        if (order.getStatus() != LabOrderStatus.IN_PROGRESS) {
            throw new BusinessException(409, "Lab order must be IN_PROGRESS before results can be entered");
        }
        LabOrderItem item = items.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("LabOrderItem", "id", itemId));
        if (item.getOrder() == null || !orderId.equals(item.getOrder().getId())) {
            throw new BusinessException(404, "Lab item does not belong to this order");
        }
        item.setResultValue(requireText(request.resultValue(), "resultValue"));
        item.setReferenceRange(trimToNull(request.referenceRange()));
        item.setResultUnit(trimToNull(request.unit()));
        item.setResultFlag(trimToNull(request.resultFlag()));
        item.setComment(trimToNull(request.comment()));
        item.setPerformedByStaffId(actorUserId);
        item.setPerformedAt(Instant.now());
        item.setStatus(LabOrderItemStatus.COMPLETED);
        return LabOrderItemResponse.from(items.save(item));
    }

    @Transactional
    public LabOrderResponse finalizeOrder(UUID id, UUID actorUserId, String actorRole, String correlationId) {
        requireRole(actorRole, RESULT_FINALIZE_ROLES);
        requireActor(actorUserId);
        LabOrder order = requireOrder(id);
        if (order.getStatus() == LabOrderStatus.RESULT_AVAILABLE || order.getStatus() == LabOrderStatus.REVIEWED) {
            return LabOrderResponse.from(order);
        }
        if (order.getStatus() != LabOrderStatus.IN_PROGRESS) {
            throw invalidTransition(order, LabOrderStatus.RESULT_AVAILABLE);
        }
        boolean missingRequired = order.getItems().stream()
                .filter(LabOrderItem::isRequired)
                .anyMatch(item -> item.getStatus() != LabOrderItemStatus.COMPLETED
                        || item.getResultValue() == null || item.getResultValue().isBlank());
        if (missingRequired) {
            throw new BusinessException(409, "All required lab items must have valid results before finalization");
        }
        order.setStatus(LabOrderStatus.RESULT_AVAILABLE);
        LabOrder saved = orders.save(order);
        events.publishAllRequiredResultsAvailable(resultsEnvelope(saved, correlationId));
        return LabOrderResponse.from(saved);
    }

    @Transactional
    public LabOrderResponse markReviewed(UUID id, UUID actorUserId, String actorRole) {
        requireRole(actorRole, CLINICAL_COMMAND_ROLES);
        requireActor(actorUserId);
        LabOrder order = requireOrder(id);
        if (order.getStatus() == LabOrderStatus.REVIEWED) return LabOrderResponse.from(order);
        if (order.getStatus() != LabOrderStatus.RESULT_AVAILABLE) {
            throw invalidTransition(order, LabOrderStatus.REVIEWED);
        }
        order.setStatus(LabOrderStatus.REVIEWED);
        return LabOrderResponse.from(orders.save(order));
    }

    @Transactional
    public LabOrderResponse cancel(UUID id, UUID actorUserId, String actorRole) {
        requireRole(actorRole, CLINICAL_COMMAND_ROLES);
        requireActor(actorUserId);
        LabOrder order = requireOrder(id);
        if (order.getStatus() == LabOrderStatus.CANCELLED) return LabOrderResponse.from(order);
        if (order.getStatus() == LabOrderStatus.RESULT_AVAILABLE || order.getStatus() == LabOrderStatus.REVIEWED) {
            throw invalidTransition(order, LabOrderStatus.CANCELLED);
        }
        order.setStatus(LabOrderStatus.CANCELLED);
        order.getItems().stream()
                .filter(item -> item.getStatus() == LabOrderItemStatus.ORDERED)
                .forEach(item -> item.setStatus(LabOrderItemStatus.CANCELLED));
        return LabOrderResponse.from(orders.save(order));
    }

    private EventEnvelope readyEnvelope(LabOrder order, String correlationId) {
        Map<String, List<UUID>> itemIdsByServicePoint = new LinkedHashMap<>();
        order.getItems().forEach(item -> itemIdsByServicePoint
                .computeIfAbsent(item.getServicePointId(), ignored -> new ArrayList<>())
                .add(item.getId()));
        List<Map<String, Object>> servicePoints = itemIdsByServicePoint.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> context = new LinkedHashMap<>();
                    context.put("servicePointId", entry.getKey());
                    context.put("itemIds", entry.getValue());
                    return context;
                })
                .toList();
        Map<String, Object> payload = basePayload(order);
        payload.put("orderedByDoctorId", order.getOrderedByDoctorId());
        payload.put("servicePoints", servicePoints);
        return envelope("LabOrderReadyForExecution", order, correlationId, payload);
    }

    private EventEnvelope resultsEnvelope(LabOrder order, String correlationId) {
        Map<String, Object> payload = basePayload(order);
        payload.put("resultAvailableAt", Instant.now());
        return envelope("AllRequiredResultsAvailable", order, correlationId, payload);
    }

    private Map<String, Object> basePayload(LabOrder order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        // `orderId` is the canonical field in CF-SVC-10. Keep the legacy
        // alias while consumers are being rolled forward so a mixed-version
        // deployment does not lose a lab order event.
        payload.put("orderId", order.getId());
        payload.put("labOrderId", order.getId());
        payload.put("consultationId", order.getConsultationId());
        payload.put("patientId", order.getPatientId());
        if (order.getDepartmentId() != null) payload.put("departmentId", order.getDepartmentId());
        return payload;
    }

    private EventEnvelope envelope(String eventType, LabOrder order,
                                   String correlationId, Map<String, Object> payload) {
        UUID eventId = UUID.randomUUID();
        return new EventEnvelope(eventId, eventType, 1, order.getId(), 1,
                Instant.now(), PRODUCER,
                correlationId == null || correlationId.isBlank() ? eventId.toString() : correlationId,
                objectMapper.valueToTree(payload));
    }

    private LabOrder requireOrder(UUID id) {
        return orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("LabOrder", "id", id));
    }

    private void verifyLabQueueEntryIsInProgress(UUID labOrderId, UUID actorUserId, String actorRole) {
        // Unit tests can exercise the aggregate without a running Queue
        // Service. Production wiring always supplies the client, so a real
        // lab start cannot bypass the queue claim/start command.
        if (queueExecutionClient == null) return;
        ApiResponse<Map<String, Object>> response;
        try {
            response = queueExecutionClient.getCurrent(labOrderId, actorUserId, actorRole);
        } catch (RuntimeException exception) {
            throw new BusinessException(409, "Lab queue entry is not available");
        }
        Map<String, Object> data = response == null ? null : response.getData();
        if (data == null || !"IN_PROGRESS".equalsIgnoreCase(String.valueOf(data.get("queueStatus")))) {
            throw new BusinessException(409, "Lab queue entry must be IN_PROGRESS before lab work starts");
        }
        Object claimedUser = data.get("calledByUserId");
        if (claimedUser != null && !actorUserId.toString().equalsIgnoreCase(String.valueOf(claimedUser))) {
            throw new BusinessException(403, "Lab queue entry is claimed by another technician");
        }
        if (data.get("labOrderId") != null
                && !labOrderId.toString().equalsIgnoreCase(String.valueOf(data.get("labOrderId")))) {
            throw new BusinessException(422, "Lab queue entry does not belong to this lab order");
        }
    }

    private void enforceReadAccess(LabOrder order, UUID actorUserId, String actorRole) {
        UUID ownPatientId = patientScope(actorUserId, actorRole);
        if (!canRead(order, ownPatientId, actorRole)) {
            throw new BusinessException(403, "Cannot read another patient's lab order");
        }
    }

    private boolean canRead(LabOrder order, UUID actorUserId, String actorRole) {
        return !isPatient(actorRole) || order.getPatientId().equals(actorUserId);
    }

    private UUID patientScope(UUID actorUserId, String actorRole) {
        if (!isPatient(actorRole)) return null;
        requireActor(actorUserId);
        if (patientIdentityClient == null) return actorUserId;
        try {
            ApiResponse<Map<String, Object>> response = patientIdentityClient.getByUserId(
                    actorUserId, actorUserId, actorRole);
            Object patientId = response == null || response.getData() == null
                    ? null : response.getData().get("id");
            if (patientId == null) throw new BusinessException(403, "Unable to verify patient ownership");
            return UUID.fromString(patientId.toString());
        } catch (RuntimeException exception) {
            throw new BusinessException(403, "Unable to verify patient ownership");
        }
    }

    private boolean isPatient(String role) {
        return AppConstants.ROLE_PATIENT.equalsIgnoreCase(normalizeRole(role));
    }

    private void requireRole(String role, Set<String> allowed) {
        String normalized = normalizeRole(role);
        if (!allowed.contains(normalized)) {
            throw new BusinessException(403, "Role is not allowed for this lab operation");
        }
    }

    private void requireActor(UUID actorUserId) {
        if (actorUserId == null) throw new BusinessException(401, "Missing trusted user header");
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new BusinessException(400, field + " must not be blank");
        return value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException invalidTransition(LabOrder order, LabOrderStatus target) {
        return new BusinessException(409,
                "Cannot transition LabOrder from " + order.getStatus() + " to " + target);
    }
}
