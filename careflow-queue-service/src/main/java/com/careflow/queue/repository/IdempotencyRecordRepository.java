package com.careflow.queue.repository;

import com.careflow.queue.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByCommandNameAndScopeIdAndIdempotencyKey(
            String commandName, UUID scopeId, String idempotencyKey);
}
