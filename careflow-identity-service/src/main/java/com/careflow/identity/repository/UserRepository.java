package com.careflow.identity.repository;

import com.careflow.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
    @Modifying
    @Query(value="UPDATE identity.staff_passkey_grants SET used_at=CURRENT_TIMESTAMP WHERE user_id=:id AND used_at IS NULL",nativeQuery=true)
    int invalidateStaffPasskeyGrants(@Param("id") UUID id);
    @Modifying
    @Query(value="WITH retired AS (DELETE FROM identity.login_challenges WHERE user_id=:id) DELETE FROM identity.passkeys WHERE user_id=:id",nativeQuery=true)
    int retirePatientPasskeysForStaffPromotion(@Param("id") UUID id);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    @Query(value="SELECT EXISTS(SELECT 1 FROM identity.staff_account_provisioning WHERE user_id=:id)",nativeQuery=true)
    boolean isProvisionedStaff(@Param("id") UUID id);
}
