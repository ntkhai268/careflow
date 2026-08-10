ALTER TABLE queue.queue_entries
    ADD COLUMN IF NOT EXISTS department_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS room_display_name_snapshot VARCHAR(255),
    ADD COLUMN IF NOT EXISTS time_slot VARCHAR(20),
    ADD COLUMN IF NOT EXISTS scheduled_start_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS priority_reason_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS queue_type VARCHAR(30) NOT NULL DEFAULT 'CONSULTATION',
    ADD COLUMN IF NOT EXISTS consultation_phase VARCHAR(30),
    ADD COLUMN IF NOT EXISTS queue_class VARCHAR(20),
    ADD COLUMN IF NOT EXISTS service_point_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS consultation_id UUID,
    ADD COLUMN IF NOT EXISTS lab_order_id UUID,
    ADD COLUMN IF NOT EXISTS prescription_id UUID;

ALTER TABLE queue.queue_configs
    ADD COLUMN IF NOT EXISTS last_served_lane VARCHAR(30);
ALTER TABLE queue.queue_configs DROP CONSTRAINT IF EXISTS ck_queue_last_served_lane;
ALTER TABLE queue.queue_configs ADD CONSTRAINT ck_queue_last_served_lane
    CHECK (last_served_lane IS NULL OR last_served_lane IN ('PRIORITY','NORMAL','RESULT_REVIEW'));

UPDATE queue.queue_entries
SET consultation_phase = 'INITIAL',
    queue_class = CASE WHEN priority_level = 'PRIORITY' THEN 'PRIORITY' ELSE 'NORMAL' END
WHERE queue_type = 'CONSULTATION';

ALTER TABLE queue.queue_entries
    ALTER COLUMN queue_config_id DROP NOT NULL,
    ALTER COLUMN department_id DROP NOT NULL,
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE queue.queue_entries DROP CONSTRAINT IF EXISTS queue_entries_status_check;
ALTER TABLE queue.queue_entries DROP CONSTRAINT IF EXISTS ck_queue_entry_status;
ALTER TABLE queue.queue_entries ADD CONSTRAINT ck_queue_entry_status
    CHECK (status IN ('WAITING','QUEUED','CHECKED_IN','CALLED','IN_PROGRESS','COMPLETED','MISSED','CANCELLED'));

ALTER TABLE queue.queue_entries DROP CONSTRAINT IF EXISTS ck_queue_entry_type;
ALTER TABLE queue.queue_entries ADD CONSTRAINT ck_queue_entry_type
    CHECK (queue_type IN ('CONSULTATION','LAB_EXECUTION','PHARMACY_DISPENSING'));

ALTER TABLE queue.queue_entries DROP CONSTRAINT IF EXISTS ck_queue_consultation_phase;
ALTER TABLE queue.queue_entries ADD CONSTRAINT ck_queue_consultation_phase
    CHECK (consultation_phase IS NULL OR consultation_phase IN ('INITIAL','RESULT_REVIEW'));

ALTER TABLE queue.queue_entries DROP CONSTRAINT IF EXISTS ck_queue_class;
ALTER TABLE queue.queue_entries ADD CONSTRAINT ck_queue_class
    CHECK (queue_class IS NULL OR queue_class IN ('NORMAL','PRIORITY'));

ALTER TABLE queue.queue_entries DROP CONSTRAINT IF EXISTS ck_queue_context;
ALTER TABLE queue.queue_entries ADD CONSTRAINT ck_queue_context
    CHECK (
        (queue_type = 'CONSULTATION' AND consultation_phase IS NOT NULL AND queue_config_id IS NOT NULL)
        OR (queue_type IN ('LAB_EXECUTION','PHARMACY_DISPENSING') AND consultation_phase IS NULL AND service_point_id IS NOT NULL)
    );

DROP INDEX IF EXISTS queue.uk_queue_active_patient_department_day;
DROP INDEX IF EXISTS queue.uk_queue_single_active_service;

CREATE UNIQUE INDEX IF NOT EXISTS uk_queue_active_initial_consultation
    ON queue.queue_entries (patient_id, department_id, queue_date)
    WHERE queue_type = 'CONSULTATION' AND consultation_phase = 'INITIAL'
      AND status IN ('WAITING','CHECKED_IN','CALLED','IN_PROGRESS','MISSED');

CREATE UNIQUE INDEX IF NOT EXISTS uk_queue_active_result_review
    ON queue.queue_entries (consultation_id)
    WHERE queue_type = 'CONSULTATION' AND consultation_phase = 'RESULT_REVIEW'
      AND status IN ('QUEUED','CALLED','IN_PROGRESS','MISSED');

CREATE UNIQUE INDEX IF NOT EXISTS uk_queue_lab_order_service_point
    ON queue.queue_entries (lab_order_id, service_point_id)
    WHERE queue_type = 'LAB_EXECUTION';

CREATE UNIQUE INDEX IF NOT EXISTS uk_queue_prescription_service_point
    ON queue.queue_entries (prescription_id)
    WHERE queue_type = 'PHARMACY_DISPENSING';

CREATE INDEX IF NOT EXISTS idx_queue_service_point_active
    ON queue.queue_entries (service_point_id, queue_date, status, eligible_since_at, sequence_number);

CREATE TABLE IF NOT EXISTS queue.service_point_sequences (
    id UUID PRIMARY KEY,
    service_point_id VARCHAR(80) NOT NULL,
    queue_date DATE NOT NULL,
    last_number INTEGER NOT NULL DEFAULT 0 CHECK (last_number >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_service_point_sequence UNIQUE (service_point_id, queue_date)
);
