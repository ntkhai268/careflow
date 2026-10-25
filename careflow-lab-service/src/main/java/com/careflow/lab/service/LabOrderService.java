package com.careflow.lab.service;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.event.EventEnvelope;
import com.careflow.common.exception.BusinessException;
import com.careflow.common.exception.ResourceNotFoundException;
import com.careflow.lab.dto.request.CreateLabOrderRequest;
import com.careflow.lab.dto.request.LabOrderItemRequest;
import com.careflow.lab.dto.request.UpdateLabResultRequest;
import com.careflow.lab.dto.response.LabOrderItemResponse;
import com.careflow.lab.dto.response.LabOrderResponse;
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
    private static final Set<String> CLINICAL_COMMAND_ROLES = Set.of(AppConstants.ROLE_DOCTOR);
    private static final Set<String> LAB_COMMAND_ROLES = Set.of(AppConstants.ROLE_LAB_TECHNICIAN);
    private static final Set<String> RESULT_FINALIZE_ROLES = Set.of(AppConstants.ROLE_LAB_TECHNICIAN);

    private final LabOrderRepository orders;
    private final LabOrderItemRepository items;
    private final LabEventPublisher events;
    private final ObjectMapper objectMapper;
    private final LabAccessService access;
    private final LabCatalogService catalog;
    @Autowired private com.careflow.lab.client.LabQueueSourceClient sourceQueues;

    @Autowired
    public LabOrderService(LabOrderRepository orders, LabOrderItemRepository items,
                           LabEventPublisher events, ObjectMapper objectMapper,
                           LabAccessService access, LabCatalogService catalog) {
        this.orders = orders;
        this.items = items;
        this.events = events;
        this.objectMapper = objectMapper;
        this.access = Objects.requireNonNull(access);
        this.catalog = Objects.requireNonNull(catalog);
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
        var context=access.requireConsultation(request.consultationId(), request.patientId(), actorUserId, actorRole, true);
        UUID source=verifiedSource(context);
        access.requireServicePoints(request.items());

        LabOrder order = new LabOrder();
        order.setConsultationId(request.consultationId());
        order.setPatientId(request.patientId());
        order.setOrderedByDoctorId(actorUserId);
        // The originating visit/Queue owns its department; never trust a client UUID here.
        order.setDepartmentId(null);
        order.setSourceQueueEntryId(source);order.setSourceVerifiedBy(actorUserId);order.setSourceVerifiedAt(Instant.now());
        order.setClinicalNote(trimToNull(request.clinicalNote()));
        order.setStatus(LabOrderStatus.ORDERED);
        order.setPaymentStatus(PaymentStatus.NOT_REQUIRED);
        for (LabOrderItemRequest itemRequest : request.items()) {
            var definition = catalog.requireItem(itemRequest);
            LabOrderItem item = new LabOrderItem();
            item.setServiceCode(definition.serviceCode());
            item.setServiceName(definition.serviceName());
            item.setServicePointId(definition.servicePointId());
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
        return responseForRead(order, actorRole);
    }

    @Transactional(readOnly = true)
    public List<LabOrderResponse> getByConsultation(UUID consultationId, UUID actorUserId, String actorRole) {
        var consultation = access.requireConsultation(consultationId, null, actorUserId, actorRole, false);
        return orders.findByConsultationIdOrderByCreatedAtDesc(consultationId).stream()
                .filter(order -> consultation.patientId().equals(order.getPatientId()))
                .map(LabOrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LabOrderResponse> getByPatient(UUID patientId, UUID actorUserId, String actorRole) {
        access.requireProfile(patientId, actorUserId, actorRole);
        return orders.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
                .filter(order -> !isPatient(actorRole) || LabAccessService.isPublished(order))
                .map(order -> responseForRead(order, actorRole))
                .toList();
    }

    @Transactional
    public LabOrderResponse startOrder(UUID id, UUID actorUserId, String actorRole) {
        requireRole(actorRole, LAB_COMMAND_ROLES);
        requireActor(actorUserId);
        LabOrder order = requireOrder(id);
        access.requireTechnician(order, actorUserId, actorRole, Set.of("IN_PROGRESS"), null);
        if (order.getStatus() == LabOrderStatus.IN_PROGRESS) return LabOrderResponse.from(order);
        if (order.getStatus() != LabOrderStatus.ORDERED && order.getStatus() != LabOrderStatus.QUEUED
                && order.getStatus() != LabOrderStatus.CALLED) {
            throw invalidTransition(order, LabOrderStatus.IN_PROGRESS);
        }
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
        access.requireTechnician(order, actorUserId, actorRole, Set.of("IN_PROGRESS"), item.getServicePointId());
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
        access.requireTechnician(order, actorUserId, actorRole, LabAccessService.isPublished(order)
                ? Set.of("IN_PROGRESS", "COMPLETED") : Set.of("IN_PROGRESS"), null);
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
        if(order.getSourceQueueEntryId()==null)throw new BusinessException(409,"Bác sĩ cần đồng bộ phiếu gốc của chỉ định trước khi phát hành kết quả.");
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
        access.requireDoctorOrder(order, actorUserId, actorRole);
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
        access.requireDoctorOrder(order, actorUserId, actorRole);
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

    private UUID verifiedSource(com.careflow.lab.client.LabConsultationClient.Context context){
        if(context==null)throw new BusinessException(503,"Chưa xác minh được ca khám nguồn.");
        if(context.queueEntryId()!=null)return context.queueEntryId();
        if(context.appointmentId()!=null && sourceQueues!=null){
            try{
                var response=sourceQueues.ticket(context.appointmentId());var ticket=response==null?null:response.getData();
                if(ticket!=null && ticket.ticketId()!=null && context.appointmentId().equals(ticket.appointmentId()) && context.patientId().equals(ticket.patientId()))return ticket.ticketId();
            }catch(feign.FeignException e){throw new BusinessException(503,"Chưa đối chiếu được phiếu của lịch khám nguồn.");}
        }
        throw new BusinessException(409,"Chưa xác định được phiếu gốc. Không tự chọn lượt gần nhất của bệnh nhân.");
    }

    @Transactional
    public LabOrderResponse synchronizeReviewQueue(UUID id,UUID actor,String role,String correlationId){
        requireRole(role,CLINICAL_COMMAND_ROLES);requireActor(actor);
        var order=requireOrder(id);var context=access.requireConsultation(order.getConsultationId(),order.getPatientId(),actor,role,false);
        UUID source=verifiedSource(context);
        if(order.getSourceQueueEntryId()!=null && !source.equals(order.getSourceQueueEntryId()))throw new BusinessException(409,"Phiếu gốc đã lưu không khớp ca khám; cần kiểm tra tại cơ sở y tế.");
        if(order.getSourceQueueEntryId()==null){order.setSourceQueueEntryId(source);order.setSourceVerifiedBy(actor);order.setSourceVerifiedAt(Instant.now());orders.save(order);}
        if(Set.of(LabOrderStatus.RESULT_AVAILABLE,LabOrderStatus.REVIEWED).contains(order.getStatus()))events.publishAllRequiredResultsAvailable(resultsEnvelope(order,correlationId));
        return LabOrderResponse.from(order);
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
        if(order.getSourceQueueEntryId()!=null)payload.put("sourceQueueEntryId",order.getSourceQueueEntryId());
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

    private void enforceReadAccess(LabOrder order, UUID actorUserId, String actorRole) {
        access.requireRead(order, actorUserId, actorRole);
    }

    private LabOrderResponse responseForRead(LabOrder order, String role) {
        var response = LabOrderResponse.from(order);
        if (!isPatient(role)) return response;
        return new LabOrderResponse(response.id(), response.consultationId(), response.patientId(),
                response.orderedByDoctorId(), response.departmentId(), response.status(), response.paymentStatus(),
                null, response.items(), response.createdAt(), response.updatedAt());
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
