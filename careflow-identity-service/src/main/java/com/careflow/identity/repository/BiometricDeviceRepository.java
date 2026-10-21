package com.careflow.identity.repository;

import com.careflow.identity.domain.BiometricDevice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface BiometricDeviceRepository extends JpaRepository<BiometricDevice,UUID> {
    @Query("select d.userId from BiometricDevice d where d.id=:id")
    Optional<UUID> owner(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from BiometricDevice d where d.id=:id")
    Optional<BiometricDevice> lock(@Param("id") UUID id);
    @Modifying(flushAutomatically=true)
    @Query("update BiometricDevice d set d.revokedAt=:now where d.userId=:user and d.revokedAt is null")
    void revokeAll(@Param("user") UUID user,@Param("now") Instant now);
    @Modifying(flushAutomatically=true)
    @Query("update BiometricDevice d set d.revokedAt=:now where d.userId=:user and d.dpopJkt=:jkt and d.revokedAt is null")
    void revokeDevice(@Param("user") UUID user,@Param("jkt") String jkt,@Param("now") Instant now);
}
