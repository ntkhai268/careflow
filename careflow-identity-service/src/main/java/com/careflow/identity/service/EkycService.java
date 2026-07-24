package com.careflow.identity.service;

import com.careflow.common.exception.BusinessException;
import com.careflow.identity.dto.EkycResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class EkycService {
    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");

    public EkycResponse verify(UUID userId, MultipartFile image) {
        validateImage(image);
        String mockDocumentNumber = "MOCK-" + userId.toString()
                .replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        return new EkycResponse(
                UUID.randomUUID(), userId, mockDocumentNumber, "NGUYEN VAN A",
                LocalDate.of(1999, 1, 1), 0.98, "VERIFIED", true, Instant.now());
    }

    void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException(400, "Cần tải lên ảnh CCCD");
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(413, "Ảnh CCCD không được vượt quá 5MB");
        }
        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(415, "Chỉ hỗ trợ ảnh JPEG hoặc PNG");
        }
        try (InputStream input = image.getInputStream()) {
            byte[] signature = input.readNBytes(8);
            if (!isJpeg(signature) && !isPng(signature)) {
                throw new BusinessException(415, "Nội dung tệp không phải ảnh JPEG hoặc PNG hợp lệ");
            }
        } catch (IOException exception) {
            throw new BusinessException(400, "Không thể đọc tệp ảnh CCCD");
        }
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
    }

    private boolean isPng(byte[] bytes) {
        int[] png = {0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (bytes.length < png.length) return false;
        for (int i = 0; i < png.length; i++) {
            if ((bytes[i] & 0xff) != png[i]) return false;
        }
        return true;
    }
}
