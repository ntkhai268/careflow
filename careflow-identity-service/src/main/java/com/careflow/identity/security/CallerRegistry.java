package com.careflow.identity.security;

import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Independent private service credentials. They never replace user authorization. */
@Component
public class CallerRegistry {
    private final JsonNode callers;
    private final boolean allowLoopbackHttp;
    public CallerRegistry(ObjectMapper mapper, @Value("${security.callers-json:}") String config, @Value("${security.audit.allow-loopback-http:false}") boolean allowLoopbackHttp) throws Exception {
        this.allowLoopbackHttp=allowLoopbackHttp;
        callers = mapper.readTree(config);
        if (callers == null || !callers.isObject() || callers.isEmpty()) throw new IllegalArgumentException("Configure SECURITY_CALLERS_JSON");
        for (JsonNode caller : callers) if (caller.path("secret").asText().length() < 32 || !caller.path("scopes").isArray()) throw new IllegalArgumentException("Each caller needs an independent random secret and explicit scopes");
    }
    public void requireTransport(jakarta.servlet.http.HttpServletRequest request,String name) {
        com.careflow.common.security.ServiceTransportIdentity.require(request,name,allowLoopbackHttp);
    }
    public void require(String name, String secret) {
        if (name == null || secret == null || !callers.has(name)
                || !MessageDigest.isEqual(callers.path(name).path("secret").asText().getBytes(StandardCharsets.UTF_8),secret.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(401,"Caller service không hợp lệ");
        }
    }
    public void requireDelegation(String name,String secret,String method,String path) {
        require(name,secret);
        for (JsonNode scope : callers.path(name).path("scopes")) {
            String[] rule = scope.asText().split(":",2);
            if (rule.length == 2 && rule[0].equals(method) && (rule[1].equals(path)
                    || (rule[1].endsWith("/**") && path.startsWith(rule[1].substring(0,rule[1].length()-2))))) return;
        }
        throw new BusinessException(403,"Caller service không có scope cho thao tác này");
    }
}
