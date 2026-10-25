package com.careflow.queue.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.queue.client.ReceptionRoomClient;
import com.careflow.queue.domain.*;
import com.careflow.queue.dto.QueueConfigResponse;
import com.careflow.queue.repository.QueueConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class QueueRoomConfigurationService {
    private final QueueConfigRepository configs;
    private final ReceptionRoomClient directory;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public QueueRoomConfigurationService(QueueConfigRepository configs,ReceptionRoomClient directory,JdbcTemplate jdbc,ObjectMapper json){
        this.configs=configs;this.directory=directory;this.jdbc=jdbc;this.json=json;
    }
    public record Policy(@Min(0) long expectedVersion,@Min(1) @Max(240) int avgConsultationMinutes,
                         @Min(1) @Max(20) int nearTurnThreshold,@NotNull MissedPolicy missedPolicy,boolean active){}
    public record View(QueueConfigResponse config,long version,int maxCallAttempts,String schedulerPolicy){}
    @Transactional
    public QueueConfig ensure(String roomId,UUID expectedDepartment){
        if(roomId==null || !roomId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,49}"))throw new BusinessException(400,"Chọn phòng khám hợp lệ.");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","queue-room:"+roomId);
        var roomResponse=directory.rooms();
        if(roomResponse==null || roomResponse.getData()==null)throw new BusinessException(503,"Không thể xác minh danh mục phòng.");
        var room=roomResponse.getData().stream().filter(r->roomId.equals(r.id()) && Boolean.TRUE.equals(r.isActive()) && "CONSULTATION".equals(r.roomType())).findFirst().orElseThrow(()->new BusinessException(409,"Phòng khám không hoạt động."));
        var departmentResponse=directory.department(room.departmentCode());var department=departmentResponse==null?null:departmentResponse.getData();
        if(department==null || !Boolean.TRUE.equals(department.isActive()) || department.id()==null)throw new BusinessException(503,"Không thể xác minh khoa của phòng.");
        if(expectedDepartment!=null && !expectedDepartment.equals(department.id()))throw new BusinessException(409,"Phòng không thuộc khoa được chọn.");
        var existing=configs.findByRoomCode(roomId);
        if(existing.isPresent()){
            if(!department.id().equals(existing.get().getDepartmentId()))throw new BusinessException(409,"Khoa/phòng không khớp cấu hình đã lưu.");
            return configs.findByIdForUpdate(existing.get().getId()).orElseThrow();
        }
        var template=configs.findAllByDepartmentIdOrderByRoomCodeAsc(department.id()).stream().findFirst().orElse(null);
        QueueConfig result=new QueueConfig();result.setDepartmentId(department.id());result.setDepartmentNameSnapshot(department.name());result.setRoomCode(roomId);
        result.setQueuePrefix(template==null?room.departmentCode().substring(0,Math.min(3,room.departmentCode().length())):template.getQueuePrefix());
        if(template!=null){result.setAvgConsultationMinutes(template.getAvgConsultationMinutes());result.setNearTurnThreshold(template.getNearTurnThreshold());result.setMissedPolicy(template.getMissedPolicy());}
        return configs.saveAndFlush(result);
    }
    @Transactional
    public View get(String roomId){return view(ensure(roomId,null));}
    @Transactional
    public View save(String roomId,Policy request,UUID actor){
        QueueConfig config=ensure(roomId,null);
        if(config.getPolicyVersion()!=request.expectedVersion())throw new BusinessException(409,"Cấu hình phòng đã thay đổi. Hãy tải lại.");
        config.setAvgConsultationMinutes(request.avgConsultationMinutes());config.setNearTurnThreshold(request.nearTurnThreshold());config.setMissedPolicy(request.missedPolicy());config.setActive(request.active());config.setPolicyVersion(config.getPolicyVersion()+1);
        configs.saveAndFlush(config);
        try{jdbc.update("INSERT INTO queue.room_policy_audit(id,queue_config_id,actor_id,policy_version,settings) VALUES (?,?,?,?,?::jsonb)",UUID.randomUUID(),config.getId(),actor,config.getPolicyVersion(),json.writeValueAsString(request));}catch(com.fasterxml.jackson.core.JsonProcessingException error){throw new IllegalStateException(error);}
        return view(config);
    }
    public void lockPatient(UUID patient,UUID department,java.time.LocalDate date){
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","queue-patient:"+patient+":"+department+":"+date);
    }
    private View view(QueueConfig config){return new View(QueueConfigResponse.from(config),config.getPolicyVersion(),3,"ROUND_ROBIN_PRIORITY_NORMAL_RESULT_REVIEW");}
}
