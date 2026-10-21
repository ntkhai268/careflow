package com.careflow.identity.security;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import static org.mockito.Mockito.*;
public final class SigningTestKeys {
    private static final KeyPair PAIR;
    static { try { var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);PAIR=generator.generateKeyPair(); } catch(Exception exception){throw new IllegalStateException(exception);} }
    public static JwtSigningKeys keys() {
        var keys=mock(JwtSigningKeys.class);
        lenient().when(keys.privateKey()).thenReturn(PAIR.getPrivate());
        lenient().when(keys.publicKey()).thenReturn((RSAPublicKey)PAIR.getPublic());
        lenient().when(keys.verificationKey("test-key")).thenReturn((RSAPublicKey)PAIR.getPublic());
        lenient().when(keys.kid()).thenReturn("test-key");return keys;
    }
}
