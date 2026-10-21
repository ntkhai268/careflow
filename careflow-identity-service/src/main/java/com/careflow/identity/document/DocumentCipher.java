package com.careflow.identity.document;

import com.careflow.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/** Dedicated, operator-owned key outside Git; independent from email/outbox keys. */
@Component
public class DocumentCipher {
    private final byte[] key;
    public DocumentCipher(@Value("${identity.document-key-file:}") String file) {
        try {
            key=file.isBlank()?null:Base64.getDecoder().decode(Files.readString(Path.of(file)).strip());
            if(key!=null && key.length!=32) throw new IllegalArgumentException();
        } catch(Exception failure) { throw new IllegalArgumentException("Invalid private identity document key file"); }
    }
    public boolean ready() { return key!=null; }
    public byte[] seal(UUID id,byte[] plain) {
        requireReady();
        try {
            byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(("identity-document:"+id).getBytes(StandardCharsets.US_ASCII));
            byte[] encrypted=cipher.doFinal(plain),result=Arrays.copyOf(nonce,12+encrypted.length);
            System.arraycopy(encrypted,0,result,12,encrypted.length);return result;
        } catch(Exception failure) { throw unavailable(); }
    }
    public byte[] open(UUID id,byte[] encrypted) {
        requireReady();
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Arrays.copyOf(encrypted,12)));
            cipher.updateAAD(("identity-document:"+id).getBytes(StandardCharsets.US_ASCII));
            return cipher.doFinal(Arrays.copyOfRange(encrypted,12,encrypted.length));
        } catch(Exception failure) { throw unavailable(); }
    }
    public String fingerprint(byte[] plain) {
        requireReady();
        try {
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(plain));
        } catch(Exception failure) { throw unavailable(); }
    }
    public void requireReady() { if(!ready()) throw unavailable(); }
    private BusinessException unavailable() { return new BusinessException(503,"Dịch vụ đối chiếu giấy tờ chưa sẵn sàng."); }
}
