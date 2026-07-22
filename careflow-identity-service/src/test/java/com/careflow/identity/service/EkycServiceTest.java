package com.careflow.identity.service;

import com.careflow.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EkycServiceTest {
    private final EkycService service = new EkycService();

    @Test
    void acceptsPngSignatureAndMarksResponseAsMock() {
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        var response = service.verify(UUID.randomUUID(),
                new MockMultipartFile("image", "cccd.png", "image/png", png));

        assertThat(response.mock()).isTrue();
        assertThat(response.status()).isEqualTo("VERIFIED");
        assertThat(response.documentNumber()).startsWith("MOCK-");
    }

    @Test
    void rejectsFakeBytesEvenWhenClientClaimsImageMimeType() {
        var fake = new MockMultipartFile("image", "fake.png", "image/png", "not-an-image".getBytes());

        assertThatThrownBy(() -> service.verify(UUID.randomUUID(), fake))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(415);
    }

    @Test
    void rejectsUnsupportedContentType() {
        var gif = new MockMultipartFile("image", "cccd.gif", "image/gif", "GIF89a".getBytes());

        assertThatThrownBy(() -> service.verify(UUID.randomUUID(), gif))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(415);
    }
}
