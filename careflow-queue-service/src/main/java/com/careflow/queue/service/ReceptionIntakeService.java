package com.careflow.queue.service;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.queue.client.DirectoryClient;
import com.careflow.queue.client.PatientIntakeClient;
import com.careflow.queue.client.ReceptionRoomClient;
import com.careflow.queue.dto.ManualIntakeRequest;
import com.careflow.queue.dto.QueueEntryResponse;
import feign.FeignException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import java.util.UUID;

@Service
public class ReceptionIntakeService {
    private final QueueManagementService queues;
    private final DirectoryClient directory;
    private final PatientIntakeClient patients;
    private final ReceptionRoomClient rooms;

    public ReceptionIntakeService(QueueManagementService queues, DirectoryClient directory, PatientIntakeClient patients,
                                  ReceptionRoomClient rooms) {
        this.queues = queues;
        this.directory = directory;
        this.patients = patients;
        this.rooms = rooms;
    }

    @Transactional
    public QueueEntryResponse intake(ManualIntakeRequest request, UUID actor, String role,
                                     String key, String correlationId) {
        if (actor == null || role == null) throw new BusinessException(401, "Thiếu phiên đăng nhập");
        if (!Set.of("STAFF", "ADMIN").contains(role)) throw new BusinessException(403, "Không có quyền tiếp nhận");
        if ("STAFF".equals(role) && request.priorityLevel() != com.careflow.queue.domain.PriorityLevel.WALK_IN) {
            throw new BusinessException(403, "Tiếp nhận STAFF chỉ tạo lượt WALK_IN; không tự cấp quyền phân loại ưu tiên hoặc cấp cứu");
        }
        var config=request.roomId()==null?queues.requireLockedConfig(request.departmentId()):queues.requireLockedRoomConfig(request.roomId(),request.departmentId());
        ApiResponse<java.util.List<ReceptionRoomClient.ReceptionRoom>> catalog;
        try { catalog = rooms.rooms(); }
        catch (RuntimeException unavailable) { throw new BusinessException(503, "Không thể xác minh phòng tiếp nhận"); }
        if (catalog == null || catalog.getData() == null || catalog.getData().stream().noneMatch(room ->
                config.getRoomCode().equals(room.id()) && Boolean.TRUE.equals(room.isActive())
                        && "CONSULTATION".equals(room.roomType()))) {
            throw new BusinessException(403, "Phòng tiếp nhận không hoạt động");
        }
        if ("STAFF".equals(role)) {
            ApiResponse<Boolean> access;
            try { access = directory.hasStaffRoomAccess(actor, config.getRoomCode()); }
            catch (RuntimeException unavailable) { throw new BusinessException(503, "Không thể xác minh phân công tiếp nhận"); }
            if (access == null || !Boolean.TRUE.equals(access.getData())) {
                throw new BusinessException(403, "Chưa được phân công phòng tiếp nhận này");
            }
        }
        ApiResponse<UUID> patient;
        try { patient = patients.owner(request.patientId()); }
        catch (FeignException missingOrDenied) {
            if (missingOrDenied.status() == 404) throw new BusinessException(404, "Hồ sơ bệnh nhân không tồn tại");
            if (missingOrDenied.status() == 403 || missingOrDenied.status() == 401) {
                throw new BusinessException(403, "Không có quyền tra cứu hồ sơ tiếp nhận");
            }
            throw new BusinessException(503, "Không thể xác minh hồ sơ tiếp nhận");
        }
        if (patient == null || patient.getStatus() != 200) throw new BusinessException(503, "Không thể xác minh hồ sơ tiếp nhận");
        UUID owner = patient.getData();
        if (request.userId() != null && !request.userId().equals(owner)) {
            throw new BusinessException(403, "userId không khớp chủ hồ sơ bệnh nhân");
        }
        if(request.serviceCode()!=null){
            try{
                var service=rooms.service(request.serviceCode());
                if(service==null || service.getData()==null || !service.getData().active() || !request.serviceCode().equals(service.getData().code()))throw new BusinessException(409,"Dịch vụ không hoạt động.");
            }catch(FeignException e){throw new BusinessException(e.status()==404?409:503,"Không thể xác minh dịch vụ khám.");}
        }
        String patientName=null;
        if(request.roomId()!=null){
            ApiResponse<PatientIntakeClient.ReceptionPatient> profile;
            try{profile=patients.minimum(request.patientId(),config.getRoomCode());}
            catch(FeignException e){throw new BusinessException(e.status()==403?403:503,"Không thể xác minh hồ sơ trong phòng tiếp nhận.");}
            if(profile==null || profile.getData()==null || !request.patientId().equals(profile.getData().id()) || !java.util.Objects.equals(owner,profile.getData().userId()) || profile.getData().fullName()==null)throw new BusinessException(503,"Không thể xác minh tên hồ sơ tiếp nhận.");
            patientName=profile.getData().fullName();
        }
        return queues.manualIntake(new ManualIntakeRequest(request.patientId(),owner,request.departmentId(),request.priorityLevel(),request.roomId(),request.serviceCode()),actor,key,correlationId,patientName);
    }
}
