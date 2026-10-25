package com.careflow.consultation.handover;

import com.careflow.common.exception.BusinessException;
import com.careflow.consultation.client.DirectoryClient;
import com.careflow.consultation.client.dto.DoctorProfileResponse;
import com.careflow.consultation.model.*;
import com.careflow.consultation.repository.ConsultationRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class HandoverService {
    private static final Set<ConsultationStatus> OPEN = EnumSet.of(ConsultationStatus.IN_PROGRESS,
        ConsultationStatus.AWAITING_CLS, ConsultationStatus.AWAITING_REVIEW, ConsultationStatus.READY_TO_COMPLETE);
    private final ConsultationRepository consultations;
    private final HandoverRepository handovers;
    private final CoordinatorRepository coordinators;
    private final CoordinatorAuditRepository permissionAudit;
    private final DirectoryClient directory;
    private final DoctorWorkloadLock workload;
    private final com.careflow.consultation.client.ConsultationQueueClient queue;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    public record CaseSummary(UUID id, UUID patientId, String patientName, UUID appointmentId, UUID doctorId, String doctorName, ConsultationStatus status) {}
    public record Workspace(boolean coordinator, List<DoctorProfileResponse> doctors, List<CaseSummary> consultations, List<Handover> handovers) {}
    public record PatientAccess(boolean allowed, List<UUID> supersededAppointmentIds) {}

    @Transactional(readOnly=true)
    public PatientAccess patientAccess(UUID patientId, UUID actor, String role) {
        doctor(actor, role);
        var cases = consultations.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
            .filter(c -> c.getStatus() != ConsultationStatus.CANCELLED && c.getStatus() != ConsultationStatus.TRANSFERRED).toList();
        return new PatientAccess(cases.stream().anyMatch(c -> actor.equals(c.currentDoctorId())),
            cases.stream().filter(c -> actor.equals(c.getDoctorId()) && !actor.equals(c.currentDoctorId()))
                .map(Consultation::getAppointmentId).filter(Objects::nonNull).toList());
    }

    @Transactional(readOnly=true)
    public Workspace workspace(UUID actor, String role) {
        doctor(actor, role);
        DoctorProfileResponse profile = activeProfile(actor);
        boolean coordinator = coordinators.existsByDoctorIdAndActiveTrue(actor);
        List<CaseSummary> cases = consultations.findByStatusIn(OPEN).stream()
            .filter(c -> actor.equals(c.currentDoctorId()) || (coordinator && sameLocation(profile, profile(c.currentDoctorId()))))
            .map(c -> new CaseSummary(c.getId(), c.getPatientId(), c.getPatientName(), c.getAppointmentId(), c.currentDoctorId(), c.currentDoctorName(), c.getStatus())).toList();
        List<DoctorProfileResponse> doctors;
        try {
            var response = directory.getDoctors(profile.getDepartmentCode());
            if (response == null || response.getStatus() != 200 || response.getData() == null) throw unavailable();
            doctors = response.getData().stream().filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .filter(p -> sameLocation(profile, p)).toList();
        } catch (FeignException e) { throw unavailable(); }
        Map<UUID,Handover> visible = new HashMap<>();
        handovers.findByToDoctorIdOrFromDoctorIdOrInitiatedByOrderByCreatedAtDesc(actor, actor, actor).forEach(h -> visible.put(h.getId(),h));
        if (coordinator) handovers.findByRoomIdAndStatus(profile.getAssignedRoomId(), Handover.Status.PENDING).stream()
            .filter(h -> profile.getDepartmentCode().equals(h.getDepartmentCode())).forEach(h -> visible.put(h.getId(),h));
        return new Workspace(coordinator, doctors, cases, visible.values().stream()
            .sorted(Comparator.comparing(Handover::getCreatedAt).reversed()).toList());
    }

    @Transactional
    public Handover request(HandoverRequests.Create request, UUID actor, String role) {
        doctor(actor, role);
        Consultation consultation = locked(request.consultationId());
        requireOpen(consultation);
        DoctorProfileResponse source = profile(consultation.currentDoctorId());
        DoctorProfileResponse initiator = activeProfile(actor);
        boolean exceptional = !actor.equals(consultation.currentDoctorId());
        if (exceptional && (!coordinators.existsByDoctorIdAndActiveTrue(actor) || !sameLocation(initiator, source))) {
            throw new BusinessException(403, "Chỉ bác sĩ phụ trách hoặc điều phối chuyên môn cùng phòng được yêu cầu bàn giao");
        }
        String actualRoom = verifiedRoom(consultation);
        if (!actualRoom.equals(source.getAssignedRoomId())) throw new BusinessException(422, "Phân công bác sĩ không khớp phòng của phiếu khám; cần điều phối trước khi bàn giao");
        DoctorProfileResponse target = activeProfile(request.toDoctorId());
        if (consultation.currentDoctorId().equals(request.toDoctorId()) || !sameLocation(source, target)
                || !sameLocation(initiator, target)) throw new BusinessException(422, "Bác sĩ nhận phải khác bác sĩ giao, cùng khoa và phòng đang phân công");
        var history = handovers.findByConsultationIdOrderByCreatedAtDesc(consultation.getId());
        var pending = history.stream().filter(h -> h.getStatus() == Handover.Status.PENDING).findFirst();
        if (pending.isPresent()) {
            Handover h = pending.get();
            if (actor.equals(h.getInitiatedBy()) && sameRequest(h, request)) return h;
            throw new BusinessException(409, "Ca khám đã có yêu cầu bàn giao đang chờ");
        }
        Handover h = new Handover();
        h.setConsultationId(consultation.getId()); h.setPatientId(consultation.getPatientId()); h.setPatientName(consultation.getPatientName());
        h.setFromDoctorId(consultation.currentDoctorId()); h.setToDoctorId(target.getUserId());
        h.setInitiatedBy(actor); h.setExceptional(exceptional);
        h.setFromDoctorName(source.getFullName()); h.setToDoctorName(target.getFullName());
        h.setDepartmentCode(source.getDepartmentCode()); h.setRoomId(source.getAssignedRoomId());
        h.setReason(text(request.reason())); h.setSituation(text(request.situation()));
        h.setBackground(text(request.background())); h.setAssessment(text(request.assessment()));
        h.setRecommendation(text(request.recommendation()));
        return handovers.saveAndFlush(h);
    }

    @Transactional
    public Handover decide(UUID id, String action, String note, UUID actor, String role) {
        doctor(actor, role);
        UUID consultationId = handovers.findConsultationId(id).orElseThrow(() -> new BusinessException(404, "Không tìm thấy yêu cầu bàn giao"));
        Consultation consultation = locked(consultationId);
        // Every decision on this consultation takes the same lock, including retries.
        Handover h = handovers.findById(id).orElseThrow(() -> new BusinessException(404, "Không tìm thấy yêu cầu bàn giao"));
        boolean receiver = actor.equals(h.getToDoctorId());
        if ((action.equals("accept") || action.equals("reject")) && !receiver) throw new BusinessException(403, "Chỉ bác sĩ nhận được xác nhận hoặc từ chối");
        if (action.equals("cancel") && !actor.equals(h.getInitiatedBy()) && !actor.equals(h.getFromDoctorId()) && !canCoordinate(h, actor)) throw new BusinessException(403, "Không được hủy yêu cầu của bác sĩ khác");
        Handover.Status desired = switch(action) {
            case "accept" -> Handover.Status.ACCEPTED;
            case "reject" -> Handover.Status.REJECTED;
            case "cancel" -> Handover.Status.CANCELLED;
            default -> throw new BusinessException(400, "Thao tác bàn giao không hợp lệ");
        };
        if (h.getStatus() == desired && actor.equals(h.getDecidedBy())) return h;
        if (h.getStatus() != Handover.Status.PENDING) throw new BusinessException(409, "Yêu cầu bàn giao đã được xử lý");
        if (desired == Handover.Status.ACCEPTED) {
            requireOpen(consultation);
            if (!h.getFromDoctorId().equals(consultation.currentDoctorId())) throw new BusinessException(409, "Bác sĩ phụ trách đã thay đổi; cần yêu cầu bàn giao mới");
            if (!h.getRoomId().equals(verifiedRoom(consultation))) throw new BusinessException(409, "Phòng của phiếu khám đã thay đổi");
            DoctorProfileResponse target = activeProfile(actor);
            DoctorProfileResponse source = profile(h.getFromDoctorId());
            if (!sameLocation(source, target) || !Objects.equals(h.getRoomId(), target.getAssignedRoomId())
                || !Objects.equals(h.getDepartmentCode(), target.getDepartmentCode())) throw new BusinessException(409, "Phân công khoa/phòng đã thay đổi");
            if (h.isExceptional()) {
                var initiator = activeProfile(h.getInitiatedBy());
                if (!coordinators.existsByDoctorIdAndActiveTrue(h.getInitiatedBy()) || !sameLocation(initiator, target)) throw new BusinessException(409, "Quyền điều phối đã thay đổi");
            }
            workload.acquire(actor);
            if (consultation.getStatus() == ConsultationStatus.IN_PROGRESS && !consultations.findCurrentByDoctorAndStatus(actor, ConsultationStatus.IN_PROGRESS).isEmpty()) {
                throw new BusinessException(409, "Bác sĩ nhận đang có ca khám chưa hoàn tất");
            }
            try {
                h.setClinicalSnapshot(mapper.writeValueAsString(new com.careflow.consultation.mapper.ConsultationMapper().toResponse(consultation)));
            } catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new BusinessException(503, "Không thể lưu lịch sử ca trước bàn giao"); }
            consultation.setResponsibleDoctorId(actor); consultation.setResponsibleDoctorName(target.getFullName());
            consultations.saveAndFlush(consultation);
        }
        h.setStatus(desired); h.setDecidedBy(actor); h.setDecidedAt(Instant.now()); h.setDecisionNote(text(note));
        return handovers.saveAndFlush(h);
    }

    @Transactional(readOnly=true)
    public List<HandoverCoordinator> grants(UUID actor, String role) { admin(actor, role); return coordinators.findAll(); }

    @Transactional
    public HandoverCoordinator grant(HandoverRequests.Grant request, UUID actor, String role) {
        admin(actor, role); activeProfile(request.doctorId());
        HandoverCoordinator grant = coordinators.findById(request.doctorId()).orElseGet(HandoverCoordinator::new);
        if (grant.isActive()) return grant;
        grant.setDoctorId(request.doctorId()); grant.setActive(true); grant.setGrantedBy(actor);
        grant.setGrantedAt(Instant.now()); grant.setGrantReason(text(request.reason()));
        grant.setRevokedBy(null); grant.setRevokedAt(null); grant.setRevokeReason(null);
        auditGrant(grant.getDoctorId(), actor, true, request.reason());
        return coordinators.save(grant);
    }

    @Transactional
    public HandoverCoordinator revoke(UUID id, String reason, UUID actor, String role) {
        admin(actor, role);
        var grant = coordinators.findById(id).orElseThrow(() -> new BusinessException(404, "Chưa cấp quyền điều phối"));
        if (!grant.isActive()) return grant;
        grant.setActive(false); grant.setRevokedBy(actor); grant.setRevokedAt(Instant.now()); grant.setRevokeReason(text(reason));
        auditGrant(id, actor, false, reason);
        return coordinators.save(grant);
    }

    @Transactional(readOnly=true)
    public List<CoordinatorAudit> grantHistory(UUID actor, String role) { admin(actor, role); return permissionAudit.findAllByOrderByCreatedAtDesc(); }

    private void auditGrant(UUID doctor, UUID actor, boolean granted, String reason) {
        var entry = new CoordinatorAudit(); entry.setDoctorId(doctor); entry.setActorId(actor); entry.setGranted(granted); entry.setReason(text(reason)); permissionAudit.save(entry);
    }

    private String verifiedRoom(Consultation c) {
        try {
            if (c.getAppointmentId() == null) {
                if(c.getQueueEntryId()==null)throw new BusinessException(422,"Ca khám thiếu phiếu hàng đợi gốc");
                var response=queue.assignmentContext(c.getQueueEntryId());
                var source=response==null || response.getStatus()!=200?null:response.getData();
                if(source==null || !c.getQueueEntryId().equals(source.entryId()) || !c.getPatientId().equals(source.patientId())
                    || source.appointmentId()!=null || (source.consultationId()!=null && !c.getId().equals(source.consultationId()))
                    || (source.departmentCode()!=null && !Objects.equals(c.getDepartmentCode(),source.departmentCode())) || source.roomId()==null || source.roomId().isBlank())
                    throw new BusinessException(503,"Không thể xác minh phiếu hàng đợi gốc của ca khám");
                return source.roomId();
            }
            var response = queue.ticket(c.getAppointmentId());
            var ticket = response == null || response.getStatus() != 200 ? null : response.getData();
            if (ticket == null || !c.getAppointmentId().equals(ticket.appointmentId())
                || !c.getPatientId().equals(ticket.patientId()) || ticket.roomId() == null || ticket.roomId().isBlank()) {
                throw new BusinessException(503, "Không thể xác minh phòng của phiếu khám");
            }
            return ticket.roomId();
        } catch (FeignException e) { throw new BusinessException(e.status() == 403 ? 403 : 503, "Không thể xác minh phân công phiếu khám"); }
    }

    private boolean canCoordinate(Handover h, UUID actor) {
        if (!coordinators.existsByDoctorIdAndActiveTrue(actor)) return false;
        var p = activeProfile(actor);
        return h.getRoomId().equals(p.getAssignedRoomId()) && h.getDepartmentCode().equals(p.getDepartmentCode());
    }

    private Consultation locked(UUID id) { return consultations.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(404, "Không tìm thấy ca khám")); }
    private void requireOpen(Consultation c) { if (!OPEN.contains(c.getStatus())) throw new BusinessException(409, "Không bàn giao ca đã đóng"); }
    private void doctor(UUID actor, String role) { if (actor == null || !"DOCTOR".equals(role)) throw new BusinessException(403, "Chức năng dành cho bác sĩ"); }
    private void admin(UUID actor, String role) { if (actor == null || !"ADMIN".equals(role)) throw new BusinessException(403, "Chỉ quản trị được quản lý quyền điều phối"); }
    private DoctorProfileResponse profile(UUID id) {
        try {
            var response = directory.getDoctorByUserId(id);
            var p = response == null || response.getStatus() != 200 ? null : response.getData();
            if (p == null || !id.equals(p.getUserId()) || p.getAssignedRoomId() == null || p.getDepartmentCode() == null || p.getFullName() == null) throw unavailable();
            return p;
        } catch (FeignException e) { if (e.status() == 404) throw new BusinessException(422, "Không tìm thấy phân công bác sĩ"); throw unavailable(); }
    }
    private DoctorProfileResponse activeProfile(UUID id) { var p = profile(id); if (!Boolean.TRUE.equals(p.getIsActive())) throw new BusinessException(422, "Bác sĩ không hoạt động"); return p; }
    private boolean sameLocation(DoctorProfileResponse a, DoctorProfileResponse b) { return a != null && b != null && a.getAssignedRoomId() != null && a.getDepartmentCode() != null && a.getAssignedRoomId().equals(b.getAssignedRoomId()) && a.getDepartmentCode().equals(b.getDepartmentCode()); }
    private String text(String s) { if (s == null || s.isBlank()) throw new BusinessException(400, "Cần nhập đầy đủ nội dung và lý do bàn giao"); return s.trim(); }
    private boolean sameRequest(Handover h, HandoverRequests.Create r) { return h.getToDoctorId().equals(r.toDoctorId()) && h.getReason().equals(text(r.reason())) && h.getSituation().equals(text(r.situation())) && h.getBackground().equals(text(r.background())) && h.getAssessment().equals(text(r.assessment())) && h.getRecommendation().equals(text(r.recommendation())); }
    private BusinessException unavailable() { return new BusinessException(503, "Không thể xác minh phân công bác sĩ với Directory"); }
}
