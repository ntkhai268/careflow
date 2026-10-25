package com.careflow.identity.recovery;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode,String> {
    @Modifying
    @Query(value="INSERT INTO identity.password_reset_codes(email_fingerprint) VALUES (:fingerprint) ON CONFLICT DO NOTHING",nativeQuery=true)
    void ensureMailbox(@Param("fingerprint") String fingerprint);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PasswordResetCode c where c.emailFingerprint=:fingerprint")
    Optional<PasswordResetCode> lockMailbox(@Param("fingerprint") String fingerprint);

    @Query("select c.userId from PasswordResetCode c where c.challengeId=:id")
    Optional<UUID> findOwnerId(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PasswordResetCode c where c.challengeId=:id")
    Optional<PasswordResetCode> lockChallenge(@Param("id") UUID id);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update PasswordResetCode c set c.usedAt=:now where c.userId=:userId and c.usedAt is null")
    void invalidateUser(@Param("userId") UUID userId,@Param("now") Instant now);

    @Modifying
    @Query("update PasswordResetCode c set c.deliveryState=:state where c.challengeId=:id")
    void delivery(@Param("id") UUID id,@Param("state") String state);

    @Modifying
    @Query("delete from PasswordResetCode c where c.lastSentAt < :cutoff")
    void cleanup(@Param("cutoff") Instant cutoff);
}
