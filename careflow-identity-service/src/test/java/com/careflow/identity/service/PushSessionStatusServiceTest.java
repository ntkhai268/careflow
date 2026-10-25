package com.careflow.identity.service;
import com.careflow.identity.domain.*;
import com.careflow.identity.repository.RefreshTokenRepository;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class PushSessionStatusServiceTest {
    final RefreshTokenRepository tokens=mock(RefreshTokenRepository.class);final PushSessionStatusService service=new PushSessionStatusService(tokens);
    final UUID owner=UUID.randomUUID(),id=UUID.randomUUID();final User user=new User();final RefreshToken root=new RefreshToken();
    @BeforeEach void setup(){user.setId(owner);user.setRole(UserRole.PATIENT);user.setStatus(UserStatus.ACTIVE);root.setId(id);root.setUser(user);root.setExpiresAt(Instant.now().plusSeconds(3600));root.setAbsoluteExpiresAt(Instant.now().plusSeconds(3600));when(tokens.findById(id)).thenReturn(Optional.of(root));}
    @Test void genericPushMayRemainAvailableWhileInteractiveUiIsIdle(){root.setLastInteractiveAt(Instant.now().minusSeconds(4000));assertThat(service.active(owner,id)).isTrue();}
    @Test void logoutOrRevocationStopsDelivery(){root.revoke(Instant.now(),null);assertThat(service.active(owner,id)).isFalse();}
    @Test void refreshRotationFollowsOnlyTheBoundAccount(){var next=new RefreshToken();next.setId(UUID.randomUUID());next.setUser(user);next.setExpiresAt(root.getExpiresAt());next.setAbsoluteExpiresAt(root.getAbsoluteExpiresAt());root.revoke(Instant.now(),"rotated");when(tokens.findForPushByTokenHash("rotated")).thenReturn(Optional.of(next));assertThat(service.active(owner,id)).isTrue();next.revoke(Instant.now(),null);assertThat(service.active(owner,id)).isFalse();}
    @Test void foreignAccountAndInactiveUserCannotReceive(){assertThat(service.active(UUID.randomUUID(),id)).isFalse();user.setStatus(UserStatus.LOCKED);assertThat(service.active(owner,id)).isFalse();}
    @Test void expiredAndBrokenRotationFailClosed(){root.setExpiresAt(Instant.now().minusSeconds(1));assertThat(service.active(owner,id)).isFalse();root.setExpiresAt(Instant.now().plusSeconds(100));root.revoke(Instant.now(),"missing");assertThat(service.active(owner,id)).isFalse();}
    @Test void rotationCycleIsRejected(){root.revoke(Instant.now(),"cycle");when(tokens.findForPushByTokenHash("cycle")).thenReturn(Optional.of(root));assertThat(service.active(owner,id)).isFalse();}
}
