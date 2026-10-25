package com.careflow.identity.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import java.util.*;

@Component
public class JwtSigningKeys {
    private final PrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String kid;
    private final Map<String,RSAPublicKey> retiring=new LinkedHashMap<>();
    public JwtSigningKeys(@Value("${security.jwt.private-key-file:}") String privateFile,
                          @Value("${security.jwt.public-key-file:}") String publicFile,
                          @Value("${security.jwt.key-id:}") String kid) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(pem(privateFile)));
            publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(pem(publicFile)));
            if (publicKey.getModulus().bitLength() < 2048 || kid.isBlank() || kid.length() > 100) throw new IllegalArgumentException();
            this.kid = kid;
            Signature signature = Signature.getInstance("SHA256withRSA");
            byte[] challenge = "CareFlow signing key validation".getBytes(StandardCharsets.UTF_8);
            signature.initSign(privateKey); signature.update(challenge); byte[] signed = signature.sign();
            signature.initVerify(publicKey); signature.update(challenge);
            if (!signature.verify(signed)) throw new IllegalArgumentException();
        } catch (Exception exception) { throw new IllegalArgumentException("Configure a matching RSA private/public key pair (2048+ bits) and SECURITY_JWT_KEY_ID"); }
    }
    private byte[] pem(String file) throws Exception {
        if (file.isBlank()) throw new IllegalArgumentException();
        return Base64.getDecoder().decode(Files.readString(Path.of(file)).replaceAll("-----[^-]+-----", "").replaceAll("\\s", ""));
    }
    public PrivateKey privateKey() { return privateKey; }
    public RSAPublicKey publicKey() { return publicKey; }
    public String kid() { return kid; }
    public Map<String,Object> jwks() {
        var all=new LinkedHashMap<>(retiring);all.put(kid,publicKey);
        return Map.of("keys", all.entrySet().stream().map(entry->Map.of("kty","RSA","use","sig","alg","RS256","kid",entry.getKey(),
                "n",unsigned(entry.getValue().getModulus()),"e",unsigned(entry.getValue().getPublicExponent()))).toList());
    }
    public RSAPublicKey verificationKey(String keyId) {
        if(kid.equals(keyId)) return publicKey;
        var key=retiring.get(keyId);
        if(key==null) throw new io.jsonwebtoken.JwtException("Unknown signing key");
        return key;
    }
    @org.springframework.beans.factory.annotation.Autowired
    void loadRetiringKeys(@Value("${security.jwt.retiring-keys-file:}") String file) {
        if(file.isBlank()) return;
        try {
            var entries=new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(Path.of(file)));
            if(!entries.isObject() || entries.size()>5) throw new IllegalArgumentException();
            var fields=entries.fields();
            while(fields.hasNext()) {
                var entry=fields.next();
                if(entry.getKey().isBlank() || entry.getKey().length()>100 || kid.equals(entry.getKey())) throw new IllegalArgumentException();
                var key=(RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pem(entry.getValue().asText())));
                if(key.getModulus().bitLength()<2048) throw new IllegalArgumentException();
                retiring.put(entry.getKey(),key);
            }
        } catch(Exception failure) { throw new IllegalArgumentException("Invalid retiring JWT public-key ring"); }
    }

    private String unsigned(java.math.BigInteger value) {
        byte[] bytes = value.toByteArray();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes[0] == 0 ? Arrays.copyOfRange(bytes,1,bytes.length) : bytes);
    }
}
