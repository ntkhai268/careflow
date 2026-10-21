package com.careflow.identity.security;

import com.careflow.identity.domain.*;
import com.careflow.identity.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtRotationHardeningTest {
    @TempDir Path directory;
    @Test void retiringKeyAcceptsExistingSessionsAndJwksPublishesBothKeys() throws Exception {
        var old=keys("old");var current=keys("new");
        Path ring=directory.resolve("ring.json");
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(ring.toFile(),Map.of("old",directory.resolve("old.pub").toString()));
        current.loadRetiringKeys(ring.toString());
        var sessions=mock(RefreshTokenRepository.class);User user=new User();user.setId(UUID.randomUUID());
        RefreshToken session=new RefreshToken();session.setId(UUID.randomUUID());session.setUser(user);session.setDpopJkt("key");session.setExpiresAt(Instant.now().plusSeconds(600));
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        String token=new JwtService(old,60000,"issuer","aud",sessions).issue(user,session.getId());
        assertThat(new JwtService(current,60000,"issuer","aud",sessions).parse(token).userId()).isEqualTo(user.getId());
        assertThat((List<?>)current.jwks().get("keys")).hasSize(2);
        assertThatThrownBy(()->current.verificationKey("unknown")).isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
    @Test void staffPolicyRejectsLegacySessionsAndRequiresVerifiedPasskeyState() throws Exception {
        var keys=keys("policy");var sessions=mock(RefreshTokenRepository.class);
        User user=new User();user.setId(UUID.randomUUID());user.setRole(UserRole.DOCTOR);
        RefreshToken session=new RefreshToken();session.setId(UUID.randomUUID());session.setUser(user);
        session.setDpopJkt("key");session.setExpiresAt(Instant.now().plusSeconds(600));
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        var service=new JwtService(keys,60000,"issuer","aud",sessions);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"requireStaffPasskey",true);
        String token=service.issue(user,session.getId());
        assertThatThrownBy(()->service.parse(token)).isInstanceOf(io.jsonwebtoken.JwtException.class);
        session.setAuthenticationMethod("BIOMETRIC");
        assertThatThrownBy(()->service.parse(token)).isInstanceOf(io.jsonwebtoken.JwtException.class);
        session.setAuthenticationMethod("PASSKEY");
        assertThat(service.parse(token).userId()).isEqualTo(user.getId());
    }
    private JwtSigningKeys keys(String id) throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pair=generator.generateKeyPair();
        Path privateFile=directory.resolve(id+".private"),publicFile=directory.resolve(id+".pub");
        Files.writeString(privateFile,Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        Files.writeString(publicFile,Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        return new JwtSigningKeys(privateFile.toString(),publicFile.toString(),id);
    }
}
