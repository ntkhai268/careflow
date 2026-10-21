package com.careflow.common.security;

import javax.net.ssl.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

/** Per-service client certificate and private CA for authenticated internal HTTPS. */
public final class InternalTlsMaterial {
    private final SSLContext context;
    public InternalTlsMaterial(String keyStore,String keyPassword,String trustStore,String trustPassword) {
        try {
            KeyStore keys=KeyStore.getInstance("PKCS12"),trust=KeyStore.getInstance("PKCS12");
            try(var stream=Files.newInputStream(Path.of(keyStore))) { keys.load(stream,keyPassword.toCharArray()); }
            try(var stream=Files.newInputStream(Path.of(trustStore))) { trust.load(stream,trustPassword.toCharArray()); }
            var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,keyPassword.toCharArray());
            var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(trust);
            context=SSLContext.getInstance("TLS");context.init(km.getKeyManagers(),tm.getTrustManagers(),null);
            // Feign URLConnection/RestTemplate share the same certificate; verification stays enabled.
            SSLContext.setDefault(context);HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
        } catch(Exception invalid) { throw new IllegalArgumentException("Configure valid private client key/trust stores for internal TLS"); }
    }
    public SSLContext context() { return context; }
}
