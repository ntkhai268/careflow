package com.careflow.prescription.config;

import com.careflow.common.constants.AppConstants;
import com.careflow.common.exception.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

final class PrescriptionRequestHeaders {
    private PrescriptionRequestHeaders() {}

    static HttpHeaders current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw new BusinessException(401, "Authenticated request context is required");
        }
        var request = attributes.getRequest();
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() == 7) {
            throw new BusinessException(401, "Bearer token is required for provider authorization");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authorization);
        for (String name : new String[]{AppConstants.HEADER_USER_ID, AppConstants.HEADER_USER_ROLE,
                AppConstants.HEADER_CORRELATION_ID}) {
            String value = request.getHeader(name);
            if (value != null) headers.set(name, value);
        }
        return headers;
    }
}
