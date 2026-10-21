package com.careflow.patient.service;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import com.careflow.patient.client.ReceptionDirectoryClient;
import com.careflow.patient.dto.request.ReceptionPatientRequest;
import com.careflow.patient.model.Patient;
import com.careflow.patient.repository.PatientRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import com.careflow.patient.model.Gender;
import java.util.Set;
import java.util.UUID;

@Service
public class PatientReceptionService {
    private final PatientRepository patients;
    private final ReceptionDirectoryClient directory;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public PatientReceptionService(PatientRepository patients, ReceptionDirectoryClient directory) {
        this(patients,directory,null,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public PatientReceptionService(PatientRepository patients,ReceptionDirectoryClient directory,org.springframework.jdbc.core.JdbcTemplate jdbc,com.fasterxml.jackson.databind.ObjectMapper json){
        this.jdbc=jdbc;this.json=json;
        this.patients = patients;
        this.directory = directory;
    }

    @Transactional
    public ReceptionPatient create(ReceptionPatientRequest request, UUID actor, String role) {
        return create(request,actor,role,null);
    }
    @Transactional
    public ReceptionPatient create(ReceptionPatientRequest request,UUID actor,String role,UUID key){
        requireRoom(request.roomId(),actor,role);
        String fingerprint=null;
        if(jdbc!=null && key!=null){
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","reception-command:"+actor+":"+key);
            try{fingerprint=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request)));}catch(Exception failure){throw new IllegalStateException(failure);}
            var old=jdbc.queryForList("SELECT fingerprint,patient_id FROM patient_reception_commands WHERE actor_id=? AND request_id=?",actor,key);
            if(!old.isEmpty()){
                if(!fingerprint.equals(old.getFirst().get("fingerprint")))throw new BusinessException(409,"Mã tạo hồ sơ đã được dùng với thông tin khác.");
                return minimum((UUID)old.getFirst().get("patient_id"),request.roomId(),actor,role);
            }
        }
        String card = request.idCardNumber() == null || request.idCardNumber().isBlank() ? null : request.idCardNumber().trim();
        if(card!=null && jdbc!=null)jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","reception-card:"+card);
        if (card != null && patients.existsByUserIdAndIdCardNumber(null, card)) {
            throw new BusinessException(409, "Đã có hồ sơ không tài khoản với CCCD này; dùng lại hồ sơ hiện có");
        }
        Patient patient = Patient.builder().fullName(request.fullName().trim())
                .dateOfBirth(request.dateOfBirth()).gender(request.gender()).phone(request.phone())
                .idCardNumber(card).receptionCreatedByUserId(actor).receptionRoomId(request.roomId()).build();
        try { patient = patients.saveAndFlush(patient); }
        catch (DataIntegrityViolationException duplicate) { throw new BusinessException(409, "Hồ sơ tiếp nhận đã tồn tại"); }
        if(jdbc!=null){
            if(key!=null)jdbc.update("INSERT INTO patient_reception_commands(actor_id,request_id,fingerprint,patient_id) VALUES (?,?,?,?)",actor,key,fingerprint,patient.getId());
            audit(actor,request.roomId(),"CREATED",patient.getId());
        }
        return new ReceptionPatient(patient.getId(), null, patient.getFullName(), patient.getDateOfBirth(), patient.getGender());
    }

    public void requireRoom(String roomId,UUID actor,String role){
        if (actor == null || role == null) throw new BusinessException(401, "Thiếu phiên đăng nhập");
        if (!Set.of("STAFF", "ADMIN").contains(role)) throw new BusinessException(403, "Không có quyền tiếp nhận");
        boolean allowed;
        try {
            var rooms = directory.rooms();
            boolean activeRoom = rooms != null && rooms.getData() != null && rooms.getData().stream()
                    .anyMatch(room -> roomId.equals(room.id()) && Boolean.TRUE.equals(room.isActive())
                            && "CONSULTATION".equals(room.roomType()));
            var access = "STAFF".equals(role) ? directory.roomAccess(actor, roomId) : null;
            allowed = activeRoom && ("ADMIN".equals(role) || (access != null && Boolean.TRUE.equals(access.getData())));
        }
        catch (RuntimeException failure) { throw new BusinessException(503, "Không thể xác minh điểm tiếp nhận"); }
        if (!allowed) {
            throw new BusinessException(403, "Không có phòng đang hoạt động hoặc phân công tiếp nhận");
        }
    }
    @Transactional
    public java.util.List<SearchPatient> search(String roomId,String query,UUID actor,String role){
        requireRoom(roomId,actor,role);
        String value=query==null?"":query.strip();
        if(value.length()<3 || value.length()>80)throw new BusinessException(400,"Nhập từ3 đến80 ký tự để tìm hồ sơ.");
        jdbc.update("DELETE FROM patient_reception_search_rate WHERE actor_id=? AND window_at<CURRENT_TIMESTAMP-interval '1 day'",actor);
        Integer attempts=jdbc.queryForObject("INSERT INTO patient_reception_search_rate(actor_id,window_at,attempts) VALUES (?,date_trunc('minute',CURRENT_TIMESTAMP),1) ON CONFLICT(actor_id,window_at) DO UPDATE SET attempts=patient_reception_search_rate.attempts+1 RETURNING attempts",Integer.class,actor);
        if(attempts!=null && attempts>60)throw new BusinessException(429,"Vượt số lần tra cứu; hãy thử lại sau một phút.");
        String escaped="%"+value.toLowerCase(java.util.Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        var result=jdbc.query("SELECT id,user_id,full_name,date_of_birth,gender,CASE WHEN length(phone)>=4 THEN '***'||right(phone,4) ELSE NULL END AS phone FROM patients WHERE lower(full_name) LIKE ? ESCAPE '!' OR phone=? OR id_card_number=? ORDER BY full_name,id LIMIT 20",
            (rs,n)->new SearchPatient(rs.getObject("id",UUID.class),rs.getObject("user_id",UUID.class),rs.getString("full_name"),rs.getDate("date_of_birth")==null?null:rs.getDate("date_of_birth").toLocalDate(),rs.getString("gender"),rs.getString("phone")),escaped,value,value);
        audit(actor,roomId,"SEARCH",null);return result;
    }
    @Transactional(readOnly=true)
    public ReceptionPatient minimum(UUID id,String roomId,UUID actor,String role){
        requireRoom(roomId,actor,role);
        Patient patient=patients.findById(id).orElseThrow(()->new com.careflow.common.exception.ResourceNotFoundException("Patient","id",id));
        return new ReceptionPatient(patient.getId(),patient.getUserId(),patient.getFullName(),patient.getDateOfBirth(),patient.getGender());
    }
    public void audit(UUID actor,String room,String action,UUID patient){jdbc.update("INSERT INTO patient_reception_audit(id,actor_id,room_id,action,patient_id) VALUES (?,?,?,?,?)",UUID.randomUUID(),actor,room,action,patient);}
    public record SearchPatient(UUID id,UUID userId,String fullName,LocalDate dateOfBirth,String gender,String maskedPhone){}
    public record ReceptionPatient(UUID id, UUID userId, String fullName, LocalDate dateOfBirth, Gender gender) {}
}
