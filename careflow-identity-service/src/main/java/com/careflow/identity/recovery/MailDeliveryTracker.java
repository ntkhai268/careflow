package com.careflow.identity.recovery;

import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Only authenticated provider IDs/states are persisted. Raw webhook recipients/content are discarded. */
@Service
public class MailDeliveryTracker {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final String secret;
    public MailDeliveryTracker(JdbcTemplate jdbc,ObjectMapper json,@Value("${resend.webhook-secret:}") String secret) {
        this.jdbc=jdbc;this.json=json;this.secret=secret;
    }
    @Transactional
    public void accept(String id,String timestamp,String signatures,byte[] raw) {
        if(secret.isBlank()) throw new BusinessException(503,"Webhook email chưa được cấu hình.");
        if(raw.length>65536) throw new BusinessException(413,"Webhook quá lớn.");
        try {
            long time=Long.parseLong(timestamp);
            long now=Instant.now().getEpochSecond();
            if(time<now-300 || time>now+300 || id==null || !id.matches("[A-Za-z0-9_-]{1,150}") || signatures==null) throw invalid();
            byte[] key=Base64.getDecoder().decode(secret.startsWith("whsec_")?secret.substring(6):secret);
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
            mac.update((id+"."+timestamp+".").getBytes(StandardCharsets.UTF_8));byte[] expected=mac.doFinal(raw);
            boolean valid=false;
            for(String signature:signatures.split(" ")) {
                String[] parts=signature.split(",",2);
                if(parts.length==2 && "v1".equals(parts[0])) {
                    try { valid|=MessageDigest.isEqual(expected,Base64.getDecoder().decode(parts[1])); }
                    catch(IllegalArgumentException ignored) { /* Other rotated signatures may still match. */ }
                }
            }
            if(!valid) throw invalid();
            var event=json.readTree(raw);
            String provider=event.path("data").path("email_id").asText();
            if(!provider.matches("[A-Za-z0-9_-]{1,100}")) throw invalid();
            String status=switch(event.path("type").asText()) {
                case "email.sent" -> "ACCEPTED";
                case "email.delivered" -> "DELIVERED";
                case "email.bounced" -> "BOUNCED";
                case "email.complained" -> "COMPLAINED";
                case "email.failed", "email.suppressed" -> "FAILED";
                case "email.delivery_delayed" -> "DELAYED";
                default -> null;
            };
            if(status==null) return;
            Instant occurred=Instant.parse(event.path("created_at").asText());
            jdbc.update("INSERT INTO identity.mail_delivery_events(event_id,provider_message_id,delivery_status,occurred_at) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",id,provider,status,Timestamp.from(occurred));
            reconcile(jdbc,provider);
        } catch(BusinessException exception) { throw exception; }
        catch(org.springframework.dao.DataAccessException exception) { throw new BusinessException(503,"Không thể ghi nhận trạng thái email. Vui lòng thử lại."); }
        catch(Exception exception) { throw invalid(); }
    }
    static void reconcile(JdbcTemplate jdbc,String provider) {
        jdbc.update("UPDATE identity.recovery_mail_outbox SET delivery_status=COALESCE((SELECT delivery_status FROM identity.mail_delivery_events WHERE provider_message_id=? ORDER BY CASE delivery_status WHEN 'COMPLAINED' THEN 5 WHEN 'BOUNCED' THEN 5 WHEN 'FAILED' THEN 5 WHEN 'DELIVERED' THEN 4 WHEN 'DELAYED' THEN 3 ELSE 2 END DESC,occurred_at DESC LIMIT 1),delivery_status) WHERE provider_message_id=?",provider,provider);
    }
    private static BusinessException invalid() { return new BusinessException(400,"Webhook email không hợp lệ."); }
}
