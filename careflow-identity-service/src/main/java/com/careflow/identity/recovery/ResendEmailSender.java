package com.careflow.identity.recovery;

import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class ResendEmailSender implements RecoveryEmailSender {
    private final String key,from;
    private final HttpClient client;
    private final URI endpoint;
    private final ObjectMapper json;
    @Autowired
    public ResendEmailSender(@Value("${resend.api-key:}") String key,
            @Value("${resend.from:CareFlow <careflow@supersignal-resend.id.vn>}") String from,ObjectMapper json) {
        this(key,from,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),URI.create("https://api.resend.com/emails"),json);
    }
    ResendEmailSender(String key,String from,HttpClient client,URI endpoint,ObjectMapper json) {
        this.key=key;this.from=from;this.client=client;this.endpoint=endpoint;this.json=json;
    }
    @Override public boolean configured() { return !key.isBlank(); }
    @Override public void sendCode(String email,String code,UUID id) {
        sendTracked("CODE",email,code,id);
    }
    @Override public void sendChanged(String email,UUID id) {
        sendTracked("CHANGED",email,null,id);
    }
    @Override public String sendTracked(String kind,String email,String code,UUID id) {
        return switch(kind) {
            case "CODE" -> send(email,"Mã xác nhận đặt lại mật khẩu CareFlow",template("code.html").replace("{{code}}",code),
                    "CareFlow — Đặt lại mật khẩu\nMã xác nhận: "+code+"\nMã có hiệu lực 5 phút, chỉ dùng một lần. Không chia sẻ mã. Nếu không phải bạn yêu cầu, hãy bỏ qua email.","recovery-code-"+id);
            case "SIGNUP" -> send(email,"Xác minh email đăng ký CareFlow",template("signup.html").replace("{{code}}",code),
                    "CareFlow — Xác minh email đăng ký\nMã xác minh: "+code+"\nMã có hiệu lực 5 phút, chỉ dùng một lần. Không chia sẻ mã. Nếu không phải bạn đăng ký, hãy bỏ qua email.","signup-code-"+id);
            case "STAFF_INVITE" -> send(email,"Thiết lập passkey nhân viên CareFlow",template("staff-invite.html").replace("{{code}}",code),
                    "CareFlow — Thiết lập passkey nhân viên\nMã cấp quyền: "+code+"\nCó hiệu lực 15 phút, chỉ dùng một lần trên trang Thiết lập passkey nhân viên. Chỉ tiếp tục nếu bạn vừa được quản trị viên kiểm tra danh tính. Không chia sẻ mã.","staff-passkey-"+id);
            case "CHANGED" -> send(email,"Mật khẩu CareFlow của bạn đã được đặt lại",template("changed.html"),
                    "Mật khẩu tài khoản CareFlow đã được đặt lại. Các phiên đăng nhập cũ đã kết thúc. Nếu không phải bạn thực hiện, hãy liên hệ bộ phận hỗ trợ.","recovery-changed-"+id);
            default -> throw new IllegalArgumentException("Unknown transactional mail purpose");
        };
    }
    private String send(String email,String subject,String html,String text,String idempotency) {
        if(!configured()) throw unavailable();
        try {
            var body=Map.of("from",from,"to",List.of(email),"subject",subject,"html",html,"text",text);
            var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15))
                .header("Authorization","Bearer "+key).header("Content-Type","application/json")
                .header("Idempotency-Key",idempotency)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body),StandardCharsets.UTF_8)).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()<200 || response.statusCode()>=300 || !json.readTree(response.body()).hasNonNull("id")) throw unavailable();
            return json.readTree(response.body()).get("id").asText();
        } catch(InterruptedException exception) {
            Thread.currentThread().interrupt();throw unavailable();
        } catch(Exception exception) { throw unavailable(); }
    }
    private String template(String name) {
        try { return new ClassPathResource("templates/recovery/"+name).getContentAsString(StandardCharsets.UTF_8); }
        catch(Exception exception) { throw new IllegalStateException("Missing recovery email template"); }
    }
    private static BusinessException unavailable() { return new BusinessException(503,"Không thể gửi email lúc này. Vui lòng thử lại sau."); }
}
