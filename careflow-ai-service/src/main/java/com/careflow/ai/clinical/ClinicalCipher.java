package com.careflow.ai.clinical;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public final class ClinicalCipher {
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public ClinicalCipher(String encoded) {
        byte[] rawKey;
        try {
            rawKey = Base64.getDecoder().decode(encoded);
        } catch (Exception e) {
            throw new IllegalArgumentException("AI content key must be base64");
        }
        if (rawKey.length != 32) {
            throw new IllegalArgumentException("AI content key must contain32bytes");
        }
        this.key = new SecretKeySpec(rawKey, "AES");
    }

    public String encrypt(String value, String binding) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
            byte[] out = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("AI content could not be encrypted");
        }
    }

    public String decrypt(String value, String binding) {
        try {
            var parts = value.split("\\.");
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new GCMParameterSpec(GCM_TAG_BITS, Base64.getDecoder().decode(parts[0])));
            cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(parts[1]));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AI content could not be read");
        }
    }
}
