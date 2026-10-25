package com.careflow.ai.clinical;
import javax.crypto.*;import javax.crypto.spec.*;import java.security.*;import java.util.*;import java.nio.charset.StandardCharsets;
public final class ClinicalCipher {
    private final SecretKeySpec key;private final SecureRandom random=new SecureRandom();
    public ClinicalCipher(String encoded){byte[] k;try{k=Base64.getDecoder().decode(encoded);}catch(Exception e){throw new IllegalArgumentException("AI content key must be base64");}if(k.length!=32)throw new IllegalArgumentException("AI content key must contain32bytes");key=new SecretKeySpec(k,"AES");}
    public String encrypt(String value,String binding){try{byte[] iv=new byte[12];random.nextBytes(iv);var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));c.updateAAD(binding.getBytes(StandardCharsets.UTF_8));byte[] out=c.doFinal(value.getBytes(StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(iv)+"."+Base64.getEncoder().encodeToString(out);}catch(Exception e){throw new IllegalStateException("AI content could not be encrypted");}}
    public String decrypt(String value,String binding){try{var parts=value.split("\\.");var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])));c.updateAAD(binding.getBytes(StandardCharsets.UTF_8));return new String(c.doFinal(Base64.getDecoder().decode(parts[1])),StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException("AI content could not be read");}}
}
