package com.careflow.identity.document;

import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.util.*;

@Service
public class DocumentIdentityService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final DocumentCipher cipher;
    private final ObjectMapper json;
    public DocumentIdentityService(JdbcTemplate jdbc,TransactionTemplate tx,DocumentCipher cipher,ObjectMapper json) {
        this.jdbc=jdbc;this.tx=tx;this.cipher=cipher;this.json=json;
    }
    public record Fields(String documentNumber,String fullName,String birthDate) { }
    public record Summary(UUID id,String status,String maskedDocumentNumber,Instant createdAt,Instant reviewedAt,String reason) { }
    public record QueueItem(Summary check,String username) { }
    public record Detail(Summary check,Fields fields) { }
    public UUID confirmedMatch(UUID owner,Fields input){
        cipher.requireReady();Fields candidate=validate(input);
        var rows=jdbc.queryForList("SELECT d.id,d.payload_fingerprint FROM identity.document_checks d JOIN identity.users u ON u.id=d.user_id WHERE d.user_id=? AND d.status='CONFIRMED' AND d.reviewed_by IS NOT NULL AND u.status='ACTIVE' AND u.role='PATIENT' ORDER BY d.created_at DESC LIMIT 1",owner);
        if(rows.isEmpty() || !fingerprintMatches(candidate,(String)rows.getFirst().get("payload_fingerprint")))throw new BusinessException(409,"Hồ sơ chưa khớp thông tin đã được đối chiếu trực tiếp của tài khoản.");
        return (UUID)rows.getFirst().get("id");
    }
    public boolean ready() { return cipher.ready(); }
    public Summary latest(UUID owner) {
        var rows=jdbc.queryForList("SELECT * FROM identity.document_checks WHERE user_id=? ORDER BY created_at DESC LIMIT 1",owner);
        return rows.isEmpty()?null:summary(rows.getFirst());
    }
    public Summary submit(UUID owner,UUID requestKey,Fields input,boolean consent) {
        cipher.requireReady();
        if(!consent) throw new BusinessException(400,"Cần xác nhận thông tin và đồng ý gửi các trường đã chọn.");
        Fields fields=validate(input);byte[] plain=encode(fields);String hash=canonicalFingerprint(fields);
        return tx.execute(transaction->{
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","document:"+owner);
            var old=jdbc.queryForList("SELECT * FROM identity.document_checks WHERE user_id=? AND request_key=?",owner,requestKey);
            if(!old.isEmpty()) {
                if(!fingerprintMatches(fields,(String)old.getFirst().get("payload_fingerprint"))) throw new BusinessException(409,"Mã yêu cầu đã được dùng cho thông tin khác.");
                return summary(old.getFirst());
            }
            var current=latest(owner);
            if(current!=null && Set.of("PENDING_REVIEW","CONFIRMED").contains(current.status()))
                throw new BusinessException(409,"Đã có yêu cầu đang chờ hoặc đã được đối chiếu. Hãy tải lại trạng thái.");
            int count=jdbc.queryForObject("SELECT count(*) FROM identity.document_checks WHERE user_id=? AND created_at>now()-interval '24 hours'",Integer.class,owner);
            if(count>=3) throw new BusinessException(429,"Tối đa ba yêu cầu đối chiếu trong 24 giờ.");
            UUID id=UUID.randomUUID();String masked="********"+fields.documentNumber().substring(8);
            jdbc.update("INSERT INTO identity.document_checks(id,user_id,request_key,payload_fingerprint,sealed_fields,masked_document_number) VALUES (?,?,?,?,?,?)",id,owner,requestKey,hash,cipher.seal(id,plain),masked);
            return summary(row(id));
        });
    }
    public List<QueueItem> pending(int page) {
        if(page<0 || page>10000) throw new BusinessException(400,"Trang không hợp lệ.");
        return jdbc.queryForList("SELECT d.*,u.username FROM identity.document_checks d JOIN identity.users u ON u.id=d.user_id WHERE d.status='PENDING_REVIEW' AND d.created_at>now()-interval '7 days' ORDER BY d.created_at,d.id LIMIT 25 OFFSET ?",page*25)
                .stream().map(r->new QueueItem(summary(r),(String)r.get("username"))).toList();
    }
    public Detail detail(UUID id) {
        var row=row(id);Summary result=summary(row);
        return new Detail(result,"PENDING_REVIEW".equals(result.status())?decode(id,(byte[])row.get("sealed_fields")):null);
    }
    public Summary review(UUID reviewer,UUID id,boolean accepted,boolean originalSeen,boolean holderMatched,boolean informationMatched,String reference) {
        if(reference==null || !reference.matches("[A-Za-z0-9][A-Za-z0-9 ._:/-]{2,79}"))
            throw new BusinessException(400,"Nhập mã quầy hoặc mã biên bản 3–80 ký tự; không nhập thông tin cá nhân.");
        if(accepted && (!originalSeen || !holderMatched || !informationMatched))
            throw new BusinessException(400,"Cần đối chiếu giấy tờ gốc, người xuất trình và thông tin trước khi xác nhận.");
        return tx.execute(transaction->{
            var rows=jdbc.queryForList("SELECT * FROM identity.document_checks WHERE id=? FOR UPDATE",id);
            if(rows.isEmpty()) throw new BusinessException(404,"Không tìm thấy yêu cầu đối chiếu.");
            var row=rows.getFirst();
            if(reviewer.equals(row.get("user_id"))) throw new BusinessException(403,"Không được tự xác nhận danh tính.");
            if(!"PENDING_REVIEW".equals(summary(row).status())) throw new BusinessException(409,"Yêu cầu đã kết thúc hoặc hết hạn.");
            jdbc.update("UPDATE identity.document_checks SET status=?,reason=?,reviewed_by=?,review_reference=?,reviewed_at=now(),sealed_fields=NULL WHERE id=?",
                    accepted?"CONFIRMED":"REJECTED",accepted?"ORIGINAL_CHECKED":"IN_PERSON_CHECK_FAILED",reviewer,reference,id);
            return summary(row(id));
        });
    }
    public Summary withdraw(UUID owner,UUID id) {
        int changed=jdbc.update("UPDATE identity.document_checks SET status='WITHDRAWN',reason='OWNER_WITHDREW',sealed_fields=NULL,reviewed_at=now() WHERE id=? AND user_id=? AND status='PENDING_REVIEW'",id,owner);
        if(changed!=1) throw new BusinessException(409,"Yêu cầu không còn chờ hoặc không thuộc tài khoản này.");
        return summary(row(id));
    }
    @Scheduled(fixedDelay=3600000)
    public void cleanup() {
        jdbc.update("UPDATE identity.document_checks SET status='EXPIRED',reason='REVIEW_EXPIRED',sealed_fields=NULL,reviewed_at=now() WHERE status='PENDING_REVIEW' AND created_at<=now()-interval '7 days'");
        jdbc.update("DELETE FROM identity.document_checks WHERE status<>'CONFIRMED' AND created_at<now()-interval '90 days'");
    }
    private Map<String,Object> row(UUID id) {
        var rows=jdbc.queryForList("SELECT * FROM identity.document_checks WHERE id=?",id);
        if(rows.isEmpty()) throw new BusinessException(404,"Không tìm thấy yêu cầu đối chiếu.");return rows.getFirst();
    }
    private Summary summary(Map<String,Object> row) {
        String state=(String)row.get("status"),reason=(String)row.get("reason");
        Instant created=((Timestamp)row.get("created_at")).toInstant();
        if("PENDING_REVIEW".equals(state) && !created.plus(Duration.ofDays(7)).isAfter(Instant.now())) { state="EXPIRED";reason="REVIEW_EXPIRED"; }
        var reviewed=(Timestamp)row.get("reviewed_at");
        return new Summary((UUID)row.get("id"),state,(String)row.get("masked_document_number"),created,reviewed==null?null:reviewed.toInstant(),reason);
    }
    private Fields validate(Fields input) {
        if(input==null || input.documentNumber()==null || !input.documentNumber().matches("[0-9]{12}"))
            throw new BusinessException(400,"Số căn cước cần đúng 12 chữ số.");
        String name=input.fullName()==null?"":Normalizer.normalize(input.fullName().strip(),Normalizer.Form.NFC);
        if(name.length()<2 || name.length()>100 || !name.matches("[\\p{L}\\p{M} .'-]+"))
            throw new BusinessException(400,"Họ tên trên giấy tờ không hợp lệ.");
        try {
            LocalDate birth=LocalDate.parse(input.birthDate());
            if(birth.isAfter(LocalDate.now()) || birth.isBefore(LocalDate.of(1900,1,1))) throw new IllegalArgumentException();
        } catch(Exception failure) { throw new BusinessException(400,"Ngày sinh không hợp lệ."); }
        return new Fields(input.documentNumber(),name,input.birthDate());
    }
    private String canonicalFingerprint(Fields fields){
        String name=fields.fullName().replaceAll("\\s+"," ").toUpperCase(Locale.ROOT);
        return cipher.fingerprint(encode(new Fields(fields.documentNumber(),name,fields.birthDate())));
    }
    private boolean fingerprintMatches(Fields fields,String stored){
        // Existing confirmations erased plaintext. Accept bounded case variants for legacy HMACs.
        String name=fields.fullName();String lower=name.toLowerCase(Locale.ROOT);
        String title=java.util.regex.Pattern.compile("\\b\\p{L}").matcher(lower).replaceAll(match->match.group().toUpperCase(Locale.ROOT));
        var candidates=new LinkedHashSet<String>();candidates.add(canonicalFingerprint(fields));
        for(String variant:List.of(name,name.toUpperCase(Locale.ROOT),lower,title))candidates.add(cipher.fingerprint(encode(new Fields(fields.documentNumber(),variant,fields.birthDate()))));
        boolean matched=false;
        for(String hash:candidates)matched|=java.security.MessageDigest.isEqual(hash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),stored.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        return matched;
    }
    private byte[] encode(Fields fields) {
        try { return json.writeValueAsBytes(fields); } catch(Exception failure) { throw new BusinessException(503,"Chưa thể chuẩn bị thông tin đối chiếu."); }
    }
    private Fields decode(UUID id,byte[] payload) {
        try { return json.readValue(cipher.open(id,payload),Fields.class); } catch(Exception failure) { throw new BusinessException(503,"Chưa thể đọc thông tin đối chiếu."); }
    }
}
