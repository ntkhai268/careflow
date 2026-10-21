package com.careflow.patient.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.patient.client.ConfirmedIdentityClient;
import feign.FeignException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class ProfileLinkService {
    private final JdbcTemplate jdbc;
    private final PatientReceptionService reception;
    private final ConfirmedIdentityClient identity;
    public ProfileLinkService(JdbcTemplate jdbc,PatientReceptionService reception,ConfirmedIdentityClient identity){this.jdbc=jdbc;this.reception=reception;this.identity=identity;}
    public record Authorization(UUID requestId,Instant expiresAt){}
    public record View(UUID id,UUID patientId,String patientName,java.time.LocalDate dateOfBirth,String roomId,String status,Instant expiresAt,Instant createdAt){}
    @Transactional
    public Authorization authorize(UUID owner,String role,UUID key,String token){
        patient(owner,role);requireToken(token);if(key==null)throw new BusinessException(400,"Thiếu mã yêu cầu.");
        lock("link-owner:"+owner);
        var old=jdbc.queryForList("SELECT * FROM patient_link_authorizations WHERE id=?",key);
        if(!old.isEmpty()){
            var row=old.getFirst();if(!owner.equals(row.get("user_id")) || !hash(token).equals(row.get("token_hash")))throw new BusinessException(409,"Mã yêu cầu đã được dùng.");
            return new Authorization(key,instant(row,"expires_at"));
        }
        Integer count=jdbc.queryForObject("SELECT count(*) FROM patient_link_authorizations WHERE user_id=? AND created_at>now()-interval '5 minutes'",Integer.class,owner);
        if(count!=null && count>=5)throw new BusinessException(429,"Hãy chờ trước khi tạo mã liên kết mới.");
        Instant expiry=Instant.now().plusSeconds(300);
        jdbc.update("INSERT INTO patient_link_authorizations(id,user_id,token_hash,expires_at) VALUES (?,?,?,?)",key,owner,hash(token),Timestamp.from(expiry));
        return new Authorization(key,expiry);
    }
    @Transactional
    public View propose(UUID id,UUID patientId,String roomId,String token,String reference,boolean originalSeen,UUID actor,String role){
        reception.requireRoom(roomId,actor,role);requireToken(token);
        if(id==null || patientId==null || !originalSeen || reference==null || !reference.matches("[A-Za-z0-9][A-Za-z0-9 ._:/-]{2,79}"))throw new BusinessException(400,"Cần kiểm tra hồ sơ gốc và mã biên bản 3–80 ký tự.");
        lock("link-patient:"+patientId);
        var previous=jdbc.queryForList("SELECT * FROM patient_profile_links WHERE id=?",id);
        if(!previous.isEmpty()){
            var row=previous.getFirst();if(!actor.equals(row.get("requested_by")) || !patientId.equals(row.get("patient_id")) || !roomId.equals(row.get("room_id")) || !reference.equals(row.get("reference")))throw new BusinessException(409,"Yêu cầu đã dùng với thông tin khác.");
            return view(id);
        }
        var codes=jdbc.queryForList("SELECT * FROM patient_link_authorizations WHERE token_hash=? FOR UPDATE",hash(token));
        if(codes.isEmpty() || codes.getFirst().get("used_at")!=null || !instant(codes.getFirst(),"expires_at").isAfter(Instant.now()))throw new BusinessException(409,"Mã liên kết không hợp lệ hoặc đã hết hạn.");
        UUID owner=(UUID)codes.getFirst().get("user_id");var profile=profile(patientId);
        if(profile.get("user_id")!=null)throw new BusinessException(409,"Hồ sơ đã có chủ tài khoản.");
        UUID check=confirmed(owner,profile);
        jdbc.update("UPDATE patient_profile_links SET status='EXPIRED',decided_at=now() WHERE patient_id=? AND status='PENDING_OWNER' AND expires_at<=now()",patientId);
        Integer open=jdbc.queryForObject("SELECT count(*) FROM patient_profile_links WHERE patient_id=? AND status IN ('PENDING_OWNER','LINKED','DUPLICATE_REVIEW')",Integer.class,patientId);
        if(open!=null && open>0)throw new BusinessException(409,"Hồ sơ đang có yêu cầu cần xử lý.");
        jdbc.update("INSERT INTO patient_profile_links(id,patient_id,owner_id,room_id,requested_by,identity_check_id,reference,status,expires_at) VALUES (?,?,?,?,?,?,?,'PENDING_OWNER',now()+interval '10 minutes')",id,patientId,owner,roomId,actor,check,reference);
        jdbc.update("UPDATE patient_link_authorizations SET used_at=now() WHERE id=?",codes.getFirst().get("id"));audit(id,actor,"PROPOSED");return view(id);
    }
    @Transactional
    public View decide(UUID id,boolean accepted,UUID owner,String role){
        patient(owner,role);var initial=row(id);if(!owner.equals(initial.get("owner_id")))throw new BusinessException(403,"Yêu cầu không thuộc tài khoản này.");
        UUID patientId=(UUID)initial.get("patient_id");lock("link-patient:"+patientId);var request=locked(id);String state=(String)request.get("status");
        if(!"PENDING_OWNER".equals(state))return view(id);
        if(!instant(request,"expires_at").isAfter(Instant.now())){finish(id,"EXPIRED",owner);return view(id);}
        if(!accepted){finish(id,"REJECTED",owner);return view(id);}
        var profile=profile(patientId);if(profile.get("user_id")!=null)throw new BusinessException(409,"Chủ hồ sơ đã thay đổi.");
        UUID check=confirmed(owner,profile);if(!check.equals(request.get("identity_check_id")))throw new BusinessException(409,"Kết quả đối chiếu đã thay đổi; cần lập yêu cầu mới.");
        lock("link-owner:"+owner);
        Integer duplicates=jdbc.queryForObject("SELECT count(*) FROM patients WHERE user_id=? AND id_card_number=? AND id<>?",Integer.class,owner,profile.get("id_card_number"),patientId);
        if(duplicates!=null && duplicates>0){finish(id,"DUPLICATE_REVIEW",owner);return view(id);}
        jdbc.update("UPDATE patients SET user_id=?,updated_at=now() WHERE id=? AND user_id IS NULL",owner,patientId);
        finish(id,"LINKED",owner);return view(id);
    }
    @Transactional
    public View revoke(UUID id,UUID actor,String role){
        var initial=row(id);
        if("PATIENT".equals(role)){patient(actor,role);if(!actor.equals(initial.get("owner_id")))throw new BusinessException(403,"Không có quyền thu hồi liên kết.");}
        else reception.requireRoom((String)initial.get("room_id"),actor,role);
        UUID patientId=(UUID)initial.get("patient_id");lock("link-patient:"+patientId);var request=locked(id);
        if("REVOKED".equals(request.get("status")))return view(id);
        if(!"LINKED".equals(request.get("status")))throw new BusinessException(409,"Liên kết chưa được cấp quyền.");
        var profile=profile(patientId);
        if(!request.get("owner_id").equals(profile.get("user_id")))throw new BusinessException(409,"Chủ hồ sơ đã thay đổi; cần kiểm tra lại.");
        jdbc.update("UPDATE patients SET user_id=NULL,updated_at=now() WHERE id=?",patientId);finish(id,"REVOKED",actor);return view(id);
    }
    @Transactional
    public View rejectReview(UUID id,UUID actor,String role){
        var initial=row(id);reception.requireRoom((String)initial.get("room_id"),actor,role);lock("link-patient:"+initial.get("patient_id"));var request=locked(id);
        if("REJECTED".equals(request.get("status")))return view(id);
        if(!Set.of("DUPLICATE_REVIEW","PENDING_OWNER").contains(request.get("status")))throw new BusinessException(409,"Yêu cầu không còn chờ xử lý.");
        finish(id,"REJECTED",actor);return view(id);
    }
    @Transactional(readOnly=true)
    public List<View> mine(UUID owner,String role){patient(owner,role);return list("l.owner_id=?",owner);}
    @Transactional(readOnly=true)
    public List<View> room(String room,UUID actor,String role){reception.requireRoom(room,actor,role);return list("l.room_id=?",room);}
    private List<View> list(String predicate,Object value){return jdbc.query("SELECT l.*,p.full_name,p.date_of_birth FROM patient_profile_links l JOIN patients p ON p.id=l.patient_id WHERE "+predicate+" ORDER BY l.created_at DESC LIMIT 50",(rs,n)->new View(rs.getObject("id",UUID.class),rs.getObject("patient_id",UUID.class),rs.getString("full_name"),rs.getDate("date_of_birth")==null?null:rs.getDate("date_of_birth").toLocalDate(),rs.getString("room_id"),"PENDING_OWNER".equals(rs.getString("status")) && !rs.getTimestamp("expires_at").toInstant().isAfter(Instant.now())?"EXPIRED":rs.getString("status"),rs.getTimestamp("expires_at").toInstant(),rs.getTimestamp("created_at").toInstant()),value);}
    private View view(UUID id){var request=row(id);return list("l.id=?",id).getFirst();}
    private Map<String,Object> row(UUID id){var rows=jdbc.queryForList("SELECT * FROM patient_profile_links WHERE id=?",id);if(rows.isEmpty())throw new BusinessException(404,"Không tìm thấy yêu cầu liên kết.");return rows.getFirst();}
    private Map<String,Object> locked(UUID id){return jdbc.queryForList("SELECT * FROM patient_profile_links WHERE id=? FOR UPDATE",id).getFirst();}
    private Map<String,Object> profile(UUID id){var rows=jdbc.queryForList("SELECT * FROM patients WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw new BusinessException(404,"Không tìm thấy hồ sơ.");return rows.getFirst();}
    private UUID confirmed(UUID owner,Map<String,Object> profile){
        if(profile.get("id_card_number")==null || profile.get("date_of_birth")==null)throw new BusinessException(409,"Hồ sơ cần số căn cước và ngày sinh đã đối chiếu.");
        try{
            var response=identity.match(new ConfirmedIdentityClient.Match(owner,new ConfirmedIdentityClient.Fields((String)profile.get("id_card_number"),(String)profile.get("full_name"),profile.get("date_of_birth").toString())));
            if(response==null || response.getData()==null || response.getData().checkId()==null)throw new BusinessException(503,"Không thể xác minh định danh.");
            return response.getData().checkId();
        }catch(FeignException e){throw new BusinessException(e.status()==409?409:503,"Hồ sơ chưa khớp định danh đã xác nhận hoặc dịch vụ đối chiếu chưa sẵn sàng.");}
    }
    private void finish(UUID id,String state,UUID actor){jdbc.update("UPDATE patient_profile_links SET status=?,decided_by=?,decided_at=now() WHERE id=?",state,actor,id);audit(id,actor,state);}
    private void audit(UUID id,UUID actor,String action){jdbc.update("INSERT INTO patient_profile_link_audit(id,link_id,actor_id,action) VALUES (?,?,?,?)",UUID.randomUUID(),id,actor,action);}
    private void lock(String key){jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",key);}
    private void patient(UUID actor,String role){if(actor==null || !"PATIENT".equals(role))throw new BusinessException(403,"Cần tài khoản bệnh nhân.");}
    private void requireToken(String token){if(token==null || !token.matches("[A-Za-z0-9_-]{43}"))throw new BusinessException(400,"Mã liên kết không hợp lệ.");}
    private String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));}catch(Exception e){throw new IllegalStateException(e);}}
    private Instant instant(Map<String,Object> row,String key){return ((Timestamp)row.get(key)).toInstant();}
}
