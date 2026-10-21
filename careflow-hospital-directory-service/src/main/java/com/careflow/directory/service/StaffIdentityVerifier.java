package com.careflow.directory.service;

import com.careflow.common.dto.ApiResponse;
import com.careflow.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/** Validates the Identity user, never the unrelated Directory profile ID. */
@Service
public class StaffIdentityVerifier {
    private final RestTemplate client;
    private final String identityUrl;
    public StaffIdentityVerifier(RestTemplateBuilder builder,
            @Value("${app.identity-service.url:http://careflow-identity-service:8081}") String identityUrl) {
        this.client=builder.connectTimeout(Duration.ofSeconds(3)).readTimeout(Duration.ofSeconds(3)).build();
        this.identityUrl=identityUrl.replaceAll("/+$","");
    }
    public record Account(UUID id, String role, String status) { }
    public void require(UUID id, Set<String> roles, boolean active) {
        if(!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes context))
            throw new BusinessException(401,"Thiếu phiên quản trị khi xác minh tài khoản.");
        String authorization=context.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        if(authorization==null || !authorization.startsWith("Bearer "))
            throw new BusinessException(401,"Thiếu phiên quản trị khi xác minh tài khoản.");
        HttpHeaders headers=new HttpHeaders();headers.set(HttpHeaders.AUTHORIZATION,authorization);
        try {
            var result=client.exchange(identityUrl+"/api/users/staff/"+id,HttpMethod.GET,new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Account>>(){}).getBody();
            Account account=result==null?null:result.getData();
            if(account==null || !id.equals(account.id()))throw new BusinessException(503,"Không thể xác minh tài khoản nội bộ.");
            if(!roles.contains(account.role()) || (active && !"ACTIVE".equals(account.status())))
                throw new BusinessException(409,"Vai trò hoặc trạng thái Identity không phù hợp với phân công.");
        } catch(HttpClientErrorException error) {
            if(error.getStatusCode().value()==404)throw new BusinessException(409,"Không tìm thấy tài khoản nội bộ trong Identity.");
            if(error.getStatusCode().value()==401 || error.getStatusCode().value()==403)
                throw new BusinessException(403,"Không có quyền xác minh tài khoản nội bộ.");
            throw new BusinessException(503,"Không thể xác minh tài khoản nội bộ.");
        } catch(RestClientException unavailable) {throw new BusinessException(503,"Không thể kết nối Identity để xác minh tài khoản.");}
    }
}
