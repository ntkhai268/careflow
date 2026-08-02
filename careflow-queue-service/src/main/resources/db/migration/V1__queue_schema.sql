CREATE SCHEMA IF NOT EXISTS queue;

CREATE TABLE queue.queue_configs (
    id UUID PRIMARY KEY, department_id UUID NOT NULL UNIQUE,
    department_name_snapshot VARCHAR(100) NOT NULL, queue_prefix VARCHAR(10) NOT NULL,
    room_code VARCHAR(50), priority_ratio_n SMALLINT NOT NULL DEFAULT 2 CHECK (priority_ratio_n > 0),
    normal_ratio_m SMALLINT NOT NULL DEFAULT 1 CHECK (normal_ratio_m > 0),
    avg_consultation_minutes SMALLINT NOT NULL DEFAULT 15 CHECK (avg_consultation_minutes > 0),
    near_turn_threshold SMALLINT NOT NULL DEFAULT 3 CHECK (near_turn_threshold > 0),
    missed_policy VARCHAR(30) NOT NULL DEFAULT 'REQUEUE_BACK' CHECK (missed_policy IN ('REQUEUE_FRONT','REQUEUE_BACK','REQUIRE_MANUAL')),
    scheduler_date DATE, cycle_phase VARCHAR(20) NOT NULL DEFAULT 'PRIORITY' CHECK (cycle_phase IN ('PRIORITY','NORMAL')),
    served_in_phase SMALLINT NOT NULL DEFAULT 0 CHECK (served_in_phase >= 0),
    normal_cursor VARCHAR(20) NOT NULL DEFAULT 'APPOINTMENT' CHECK (normal_cursor IN ('APPOINTMENT','WALK_IN')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE, version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_queue_config_context UNIQUE (id, department_id)
);

CREATE TABLE queue.queue_number_sequences (
    id UUID PRIMARY KEY, queue_config_id UUID NOT NULL REFERENCES queue.queue_configs(id),
    queue_date DATE NOT NULL, last_number INTEGER NOT NULL DEFAULT 0 CHECK (last_number >= 0),
    version BIGINT NOT NULL DEFAULT 0, updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_queue_number_sequence UNIQUE (queue_config_id, queue_date)
);

CREATE TABLE queue.queue_entries (
    id UUID PRIMARY KEY, queue_config_id UUID NOT NULL, department_id UUID NOT NULL,
    appointment_id UUID UNIQUE, patient_id UUID NOT NULL, user_id UUID NOT NULL,
    queue_date DATE NOT NULL, sequence_number INTEGER NOT NULL CHECK (sequence_number > 0),
    queue_number VARCHAR(20) NOT NULL, priority_level VARCHAR(20) NOT NULL CHECK (priority_level IN ('EMERGENCY','PRIORITY','APPOINTMENT','WALK_IN')),
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING' CHECK (status IN ('WAITING','CHECKED_IN','CALLED','IN_PROGRESS','COMPLETED','MISSED','CANCELLED')),
    checked_in_at TIMESTAMPTZ, eligible_since_at TIMESTAMPTZ, called_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ, completed_at TIMESTAMPTZ, missed_at TIMESTAMPTZ, cancelled_at TIMESTAMPTZ,
    missed_count SMALLINT NOT NULL DEFAULT 0 CHECK (missed_count >= 0),
    estimated_wait_minutes INTEGER CHECK (estimated_wait_minutes IS NULL OR estimated_wait_minutes >= 0),
    version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_queue_entry_config_context FOREIGN KEY (queue_config_id, department_id)
        REFERENCES queue.queue_configs(id, department_id),
    CONSTRAINT uk_queue_number_per_day UNIQUE (queue_config_id, queue_date, queue_number)
);
CREATE INDEX idx_queue_next_candidate ON queue.queue_entries
    (queue_config_id, queue_date, status, priority_level, eligible_since_at, sequence_number);
CREATE INDEX idx_queue_patient_date ON queue.queue_entries (patient_id, queue_date);
CREATE INDEX idx_queue_user_date ON queue.queue_entries (user_id, queue_date);

CREATE TABLE queue.outbox_events (
    event_id UUID PRIMARY KEY, aggregate_type VARCHAR(50) NOT NULL, aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL, event_version INTEGER NOT NULL DEFAULT 1,
    aggregate_version BIGINT NOT NULL, exchange_name VARCHAR(100) NOT NULL, routing_key VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PUBLISHED','FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0), occurred_at TIMESTAMPTZ NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL, published_at TIMESTAMPTZ, last_error TEXT, created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_queue_outbox_pending ON queue.outbox_events (status, next_attempt_at, created_at);
CREATE INDEX idx_queue_outbox_aggregate ON queue.outbox_events (aggregate_id, aggregate_version);

CREATE TABLE queue.processed_events (
    event_id UUID NOT NULL, consumer_name VARCHAR(100) NOT NULL, event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL, PRIMARY KEY (event_id, consumer_name)
);
