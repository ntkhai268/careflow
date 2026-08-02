ALTER TABLE queue.queue_entries
    ADD COLUMN call_attempts SMALLINT NOT NULL DEFAULT 0 CHECK (call_attempts >= 0),
    ADD COLUMN near_turn_notified_at TIMESTAMPTZ;

UPDATE queue.queue_entries
SET call_attempts = CASE
    WHEN status = 'MISSED' THEN 3
    WHEN status IN ('CALLED', 'IN_PROGRESS', 'COMPLETED') THEN 1
    ELSE 0
END;

CREATE UNIQUE INDEX uk_queue_active_patient_department_day
    ON queue.queue_entries (patient_id, department_id, queue_date)
    WHERE status IN ('WAITING', 'CHECKED_IN', 'CALLED', 'IN_PROGRESS', 'MISSED');

CREATE UNIQUE INDEX uk_queue_single_active_service
    ON queue.queue_entries (queue_config_id, queue_date)
    WHERE status IN ('CALLED', 'IN_PROGRESS');

CREATE TABLE queue.idempotency_records (
    id UUID PRIMARY KEY,
    command_name VARCHAR(50) NOT NULL,
    scope_id UUID NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    result_entry_id UUID REFERENCES queue.queue_entries(id),
    empty_result BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_queue_idempotency UNIQUE (command_name, scope_id, idempotency_key),
    CONSTRAINT ck_queue_idempotency_result CHECK (
        (empty_result = TRUE AND result_entry_id IS NULL)
        OR (empty_result = FALSE AND result_entry_id IS NOT NULL)
    )
);

CREATE INDEX idx_queue_idempotency_created_at
    ON queue.idempotency_records (created_at);

ALTER TABLE queue.outbox_events
    DROP CONSTRAINT IF EXISTS outbox_events_status_check;
ALTER TABLE queue.outbox_events
    ADD CONSTRAINT ck_queue_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'DEAD'));
