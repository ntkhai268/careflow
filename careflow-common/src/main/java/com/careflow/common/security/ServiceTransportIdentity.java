package com.careflow.common.security;

import com.careflow.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.security.cert.X509Certificate;
import javax.naming.ldap.LdapName;

public final class ServiceTransportIdentity {
    private ServiceTransportIdentity() {}
    public static void require(HttpServletRequest request,String caller,boolean allowLoopbackHttp) {
        if (allowLoopbackHttp && !request.isSecure() && java.util.Set.of("127.0.0.1","0:0:0:0:0:0:0:1","::1").contains(request.getRemoteAddr())) return;
        try {
            Object value=request.getAttribute("jakarta.servlet.request.X509Certificate");
            if (!request.isSecure() || !(value instanceof X509Certificate[] chain) || chain.length==0) throw new IllegalArgumentException();
            var names=new LdapName(chain[0].getSubjectX500Principal().getName()).getRdns().stream().filter(rdn->rdn.getType().equalsIgnoreCase("CN")).toList();
            if (names.size()!=1 || !caller.equals(names.getFirst().getValue().toString())) throw new IllegalArgumentException();
        } catch(Exception invalid) { throw new BusinessException(401,"Caller service cần chứng chỉ mTLS đúng danh tính"); }
    }
}
