CREATE TABLE appointment_room_schedules (
    room_id varchar(50) PRIMARY KEY,
    version bigint NOT NULL CHECK (version > 0),
    effective_from date NOT NULL,
    definition jsonb NOT NULL,
    updated_by uuid,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE appointment_schedule_revisions (
    room_id varchar(50) NOT NULL REFERENCES appointment_room_schedules(room_id),
    version bigint NOT NULL,
    effective_from date NOT NULL,
    definition jsonb NOT NULL,
    created_by uuid,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (room_id,version)
);
CREATE INDEX idx_schedule_effective ON appointment_schedule_revisions(room_id,effective_from,version DESC);
CREATE TABLE appointment_booking_commands (
    scope_key varchar(255) PRIMARY KEY,
    fingerprint varchar(64) NOT NULL,
    appointment_id uuid NOT NULL REFERENCES appointments(id),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
